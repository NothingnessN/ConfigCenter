# SPDX-License-Identifier: Apache-2.0
# Include this directly from the top-level product makefile after GApps/product
# configuration so its overlay path precedes inherited overlay paths.

# Product makefiles are imported through an eval context where my-dir can be
# empty. ConfigCenter's documented ROM location is fixed and repo-relative.
_d4rk_config_center_product_path := packages/apps/ConfigCenter/product

$(call inherit-product, $(_d4rk_config_center_product_path)/lineageparts.mk)

PRODUCT_PACKAGE_OVERLAYS := \
    $(_d4rk_config_center_product_path)/overlay \
    $(PRODUCT_PACKAGE_OVERLAYS)

PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS := \
    $(_d4rk_config_center_product_path)/overlay \
    $(PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS)

_d4rk_config_center_product_path :=
