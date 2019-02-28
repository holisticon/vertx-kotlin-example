package apodrating

import io.vertx.junit5.VertxTestContext
import org.hamcrest.Matcher
import org.hamcrest.StringDescription

/**
 * Provides `assertThat` methods usable with Vertx-Unit.
 *
 * See [Gist at github](https://gist.github.com/cescoffier/5cbf4c69aa094ac9b1a6)
 */
object VertxMatcherAssert {

    /**
     * Checks that `actual` matches `matchers`. If not, the error is reported on the given `TestContext`.
     *
     * @param context the test context
     * @param actual  the object to test
     * @param matcher the matcher
     * @param <T>     the type of the object to test
    </T> */
    fun <T> assertThat(context: VertxTestContext, actual: T, matcher: Matcher<in T>) {
        assertThat(context, "", actual, matcher)
    }

    /**
     * Checks that `actual` matches `matchers`. If not, the error is reported on the given `TestContext`.
     *
     * @param context the test context
     * @param reason  the reason
     * @param actual  the object to test
     * @param matcher the matcher
     * @param <T>     the type of the object to test
    </T> */
    fun <T> assertThat(context: VertxTestContext, reason: String, actual: T, matcher: Matcher<in T>) {
        if (!matcher.matches(actual)) {
            val description = StringDescription()
            description.appendText(reason)
                .appendText("\nExpected: ")
                .appendDescriptionOf(matcher)
                .appendText("\n     but: ")
            matcher.describeMismatch(actual, description)
            context.failNow(IllegalStateException(description.toString()))
        }
    }

    /**
     * Checks whether or not `assertion` is `true`. If not, it reports the error on the given `TestContext`.
     *
     * @param context   the test context
     * @param reason    the reason
     * @param assertion the assertion state
     */
    fun assertThat(context: VertxTestContext, reason: String, assertion: Boolean) {
        if (!assertion) {
            context.failNow(IllegalStateException(reason))
        }
    }
}