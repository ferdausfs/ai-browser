package com.zai.aibrowser.update

/**
 * Self-update source of truth. The app checks its own GitHub releases; publishing a
 * new release (tag "vX.Y.Z") is all it takes to ship an update to installed devices.
 */
object UpdateConfig {
    const val REPO_OWNER = "ferdausfs"
    const val REPO_NAME = "ai-browser"
    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"
}
