package com.yepanywhere.android

class AndroidNotificationEventDispatcher(
    private val postNotification: (Int, AndroidNotificationContent) -> Boolean,
) {
    constructor(poster: AndroidNotificationPoster) : this(poster::post)

    fun dispatch(
        title: String?,
        body: String?,
        data: Map<String, String>,
    ): Boolean {
        val content = AndroidNotificationContent.fromPayload(
            title = title,
            body = body,
            data = data,
        ) ?: return false

        return postNotification(content.notificationId(), content)
    }
}
