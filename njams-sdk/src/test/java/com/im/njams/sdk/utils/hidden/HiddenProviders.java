package com.im.njams.sdk.utils.hidden;

/**
 * Providers whose implementation classes are not public and live in a package of their own, so that reflective
 * access from {@code com.im.njams.sdk.utils} really is subject to the accessibility check. Within one package the
 * check passes regardless, which would make such a test pass for the wrong reason.
 */
public final class HiddenProviders {

    /** Declares {@link #value()} and implements a public interface that declares it as well. */
    static class WithPublicInterface implements HiddenApi {
        @Override
        public String value() {
            return "via-interface";
        }
    }

    /** Declares a public {@code value()} without any interface declaring it. */
    static class WithoutPublicInterface {
        public String value() {
            return "direct";
        }
    }

    private HiddenProviders() {
        // static access only
    }

    public static Object withPublicInterface() {
        return new WithPublicInterface();
    }

    public static Object withoutPublicInterface() {
        return new WithoutPublicInterface();
    }
}
