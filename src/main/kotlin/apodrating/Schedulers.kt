package apodrating

import io.vertx.reactivex.core.RxHelper
import io.vertx.reactivex.core.Vertx

fun computationScheduler(vertx: Vertx) = RxHelper.scheduler(vertx)
fun ioScheduler(vertx: Vertx) = RxHelper.blockingScheduler(vertx)