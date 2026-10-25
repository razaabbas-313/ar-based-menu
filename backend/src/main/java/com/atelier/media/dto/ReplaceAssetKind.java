package com.atelier.media.dto;

/** Which visual asset of a dish is being replaced in place. */
public enum ReplaceAssetKind {
    /** The 2D cover photo (client-compressed WebP/JPEG). */
    PHOTO,
    /** The 3D model (.glb / .gltf, magic-header validated in the browser). */
    MODEL
}
