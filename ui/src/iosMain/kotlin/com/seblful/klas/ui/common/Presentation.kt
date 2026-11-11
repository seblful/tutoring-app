package com.seblful.klas.ui.common

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/** Presents [controller] over whatever is showing: the Compose window's root, or what it already presents. */
internal fun presentOnTop(controller: UIViewController) {
    @Suppress("DEPRECATION")
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    while (true) top = top.presentedViewController ?: break
    top.presentViewController(controller, animated = true, completion = null)
}
