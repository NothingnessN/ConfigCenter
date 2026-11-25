#!/usr/bin/env python3
"""Convert the owner's simple APS path artwork to an Android VectorDrawable.

This is not a general SVG/APS converter. Unsupported drawing operations fail
closed. The supplied document contains polylines/cubic curves, an affine Y flip,
rectangular clips and white paper rectangles. Paper is not part of the logo.
"""

import argparse
import math
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


ANDROID = "http://schemas.android.com/apk/res/android"


def number(value):
    return f"{value:.9g}"


def convert(source, original, output, svg, png):
    root = ET.parse(source).getroot()
    if root.tag != "Aps":
        raise ValueError("Expected the supplied APS vector document")
    allowed = {"Aps", "Group", "Page", "Canvas", "Clip", "Path", "Brush",
               "SolidBrush", "PathFigure", "PolyLine", "Point", "Bezier",
               "StartPoint", "ControlPoint1", "ControlPoint2", "EndPoint"}
    unsupported = {e.tag for e in root.iter()} - allowed
    if unsupported:
        raise ValueError(f"Unsupported operations: {unsupported}")
    pages = root.findall("./Group/Page")
    if len(pages) != 1:
        raise ValueError("Expected exactly one page")
    page = pages[0]
    width, height = float(page.get("Width")), float(page.get("Height"))
    if width <= 0 or height <= 0:
        raise ValueError("Invalid page size")
    paths, all_points = [], []
    for canvas in page.iter("Canvas"):
        for path in canvas.findall("Path"):
            brush = path.find("./Brush/SolidBrush")
            if brush is None or path.get("FillMode") != "Winding":
                raise ValueError("Unsupported fill")
            color = brush.get("Color")
            if not re.fullmatch(r"#[0-9A-Fa-f]{8}", color or ""):
                raise ValueError("Expected an ARGB solid fill")
            matrix = [float(v) for v in path.get("RenderTransform", "1 0 0 1 0 0").split()]
            if len(matrix) != 6 or not all(math.isfinite(v) for v in matrix):
                raise ValueError("Invalid affine transform")

            def point(element):
                x, y = float(element.get("X")), float(element.get("Y"))
                a, b, c, d, e, f = matrix
                result = (a * x + c * y + e, b * x + d * y + f)
                if not all(math.isfinite(v) for v in result):
                    raise ValueError("Invalid coordinate")
                return result

            commands, points = [], []
            for figure in path.findall("PathFigure"):
                previous = None
                for segment in figure:
                    if segment.tag == "PolyLine":
                        for p in segment.findall("Point"):
                            p = point(p)
                            if p == previous:
                                continue
                            commands.append(("M" if previous is None else "L")
                                            + ",".join(map(number, p)))
                            points.append(p)
                            previous = p
                    elif segment.tag == "Bezier":
                        start = point(segment.find("StartPoint"))
                        if start != previous:
                            raise ValueError("Discontinuous cubic curve")
                        control = [point(segment.find(tag)) for tag in
                                   ("ControlPoint1", "ControlPoint2", "EndPoint")]
                        commands.append("C" + " ".join(
                            ",".join(map(number, p)) for p in control))
                        points.extend(control)
                        previous = control[-1]
                    else:
                        raise ValueError(f"Unsupported segment: {segment.tag}")
                if figure.get("IsClosed") == "True":
                    commands.append("Z")
                else:
                    raise ValueError("Logo paths must be closed")
            xs, ys = zip(*points)
            # The two white rectangles cover the entire page and are paper,
            # not a second artwork color. Do not keep a white tile in Settings.
            if color.upper() == "#FFFFFFFF" and min(xs) <= 0 and min(ys) <= 0 \
                    and max(xs) >= width and max(ys) >= height:
                if any(command.startswith("C") for command in commands):
                    raise ValueError("Unexpected white artwork")
                continue
            if min(xs) < 0 or min(ys) < 0 or max(xs) > width or max(ys) > height:
                raise ValueError("Artwork outside the page clip")
            paths.append((color, " ".join(commands)))
            all_points.extend(points)
    if not paths:
        raise ValueError("No logo artwork")
    # The supplied clip (26,0)-(124,150) contains the entire artwork. Reject
    # clips that actually cut a logo instead of silently dropping clipping.
    for clip in page.iter("Clip"):
        clip_points = [(float(p.get("X")), float(p.get("Y"))) for p in clip.iter("Point")]
        if not clip_points or any(e.tag == "Bezier" for e in clip.iter()):
            raise ValueError("Only rectangular clips are supported")
        xs, ys = zip(*clip_points)
        if len(set(xs)) != 2 or len(set(ys)) != 2:
            raise ValueError("Non-rectangular clip")
        if any(not (min(xs) <= x <= max(xs) and min(ys) <= y <= max(ys))
               for x, y in all_points):
            raise ValueError("Clip intersects artwork")

    xs, ys = zip(*all_points)
    w, h = max(xs) - min(xs), max(ys) - min(ys)
    scale = 22.0 / max(w, h)
    tx = (24.0 - w * scale) / 2.0 - min(xs) * scale
    ty = (24.0 - h * scale) / 2.0 - min(ys) * scale
    colors = {color.upper() for color, _ in paths}
    tinted = len(colors) == 1
    text = original.read_text()
    header = text[:text.index("<vector")]
    vector = header + f'''<vector xmlns:android="{ANDROID}"
    android:width="88dp"
    android:height="88dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <group android:scaleX="{number(scale)}" android:scaleY="{number(scale)}"
        android:translateX="{number(tx)}" android:translateY="{number(ty)}">
'''
    preview = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">\n'
    preview += f'<g transform="translate({number(tx)} {number(ty)}) scale({number(scale)})">\n'
    for color, data in paths:
        fill = "?android:attr/colorAccent" if tinted else color
        vector += f'        <path android:fillColor="{fill}" android:fillType="nonZero"\n'
        vector += f'            android:pathData="{data}" />\n'
        preview_color = "#6750A4" if tinted else "#" + color[3:]
        preview += f'<path fill="{preview_color}" fill-rule="nonzero" d="{data}"/>\n'
    vector += '    </group>\n</vector>\n'
    preview += '</g>\n</svg>\n'
    ET.fromstring(vector)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(vector)
    svg.parent.mkdir(parents=True, exist_ok=True)
    svg.write_text(preview)
    subprocess.run(["rsvg-convert", "--width", "528", "--height", "528", "--output", str(png),
                    str(svg)], check=True)
    print(f"{len(paths)} artwork path(s), colors={sorted(colors)}, theme accent={tinted}")
    print(f"88dp x 88dp; viewport 24 x 24; content bounds {w:.6f} x {h:.6f}")
    print(f"Preview: {png} (sample accent #6750A4, transparent background)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "original", "output", "svg", "png"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    convert(args.source, args.original, args.output, args.svg, args.png)


if __name__ == "__main__":
    main()
