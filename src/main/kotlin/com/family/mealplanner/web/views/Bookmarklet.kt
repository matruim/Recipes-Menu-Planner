package com.family.mealplanner.web.views

/**
 * A one-click alternative to viewing page source.
 *
 * Dragged to the bookmarks bar once, then clicked on any recipe page: it copies
 * the page and opens the planner ready to receive it.
 *
 * It works by clipboard rather than by posting the page straight here, because
 * recipe sites are served over https while this app is served over plain http on
 * the home network. A cross-origin form post between those two is mixed content
 * and browsers block it. Opening a new tab is ordinary navigation, which is
 * allowed, and the clipboard carries the payload across.
 *
 * The window is opened first, synchronously, so it still counts as part of the
 * click and does not trip the popup blocker.
 */
fun bookmarkletHref(appOrigin: String): String {
    val script = """
        (function(){
          var d=document;
          if(!d.querySelector('script[type="application/ld+json"]')){
            alert('No recipe data found on this page.');return;
          }
          var html=d.documentElement.outerHTML;
          window.open('$appOrigin/recipes/new?paste=1','_blank');
          if(navigator.clipboard&&navigator.clipboard.writeText){
            navigator.clipboard.writeText(html)['catch'](function(){
              alert('Could not copy this page. Use Ctrl/Cmd+U and paste it by hand.');
            });
          }else{
            alert('This browser will not let the page be copied here. Use Ctrl/Cmd+U instead.');
          }
        })()
    """.trimIndent().lineSequence().joinToString("") { it.trim() }

    return "javascript:$script"
}
