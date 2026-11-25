# UI-only XML rebranding. Run with DRY=1 perl -n before perl -pi.
# Tags, comments, identifiers, resource references, properties and URLs stay intact.
BEGIN { $new = $ENV{NEW} || "D4RK OS"; }
$orig = $_;
$out = '';
for $tok (split /(<!--|-->|<[^>]*>)/, $_) {
    if ($tok eq '<!--') { $in_c = 1; $out .= $tok; next; }
    if ($tok eq '-->')  { $in_c = 0; $out .= $tok; next; }
    if ($in_c || $tok =~ /^</) { $out .= $tok; next; }
    if ($tok !~ m{https?:|github|\.com|\@|ro\.havoc}i) {
        $tok =~ s/(?<![.\w\/])(?:Havoc|HAVOC)[- ]?(?i:OS)\b/$new/g;
        $tok =~ s/(?<![.\w\/])(?:Havoc|HAVOC)/D4RK/g;
    }
    $out .= $tok;
}
print "$ARGV:$.: $out" if $ENV{DRY} && $out ne $orig;
$_ = $out;
$in_c = 0 if eof;
