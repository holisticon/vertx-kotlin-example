package apodrating.remoteproxy

import apodrating.remoteproxy.reactivex.RemoteProxyService
import io.vertx.reactivex.core.Vertx

fun createRemoteProxyServiceProxy(vertx: Vertx, address: String): RemoteProxyService {
    return RemoteProxyService(RemoteProxyServiceVertxEBProxy(vertx.delegate, address))
}