package com.zai.aibrowser

import android.app.Application

/**
 * Application entry point. Deliberately minimal: dependencies are created lazily
 * where they are needed (no service locator, no reflection) so that each agent
 * layer stays easy to test and to swap in later phases.
 */
class AiBrowserApp : Application()
