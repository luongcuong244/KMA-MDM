package com.example.kmamdm.server

import java.net.URL

class ServerUrl(serverUrl: String?) {
    var baseUrl: String
    var serverProject: String

    init {
        val url = URL(serverUrl)

        baseUrl = url.protocol + "://" + url.host
        if (url.port != -1) {
            baseUrl += ":" + url.port
        }
        serverProject = url.path
        if (serverProject.endsWith("/")) {
            serverProject = serverProject.substring(0, serverProject.length - 1)
        }
        if (serverProject.startsWith("/")) {
            serverProject = serverProject.substring(1)
        }
    }
}
