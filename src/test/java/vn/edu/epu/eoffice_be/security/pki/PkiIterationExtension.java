package vn.edu.epu.eoffice_be.security.pki;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Never changes Security properties or the production configuration. */
public final class PkiIterationExtension implements BeforeAllCallback, AfterAllCallback {
    public static final String PROPERTY = "org.bouncycastle.pkcs12.store_it_count";
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(PkiIterationExtension.class);

    @Override public void beforeAll(ExtensionContext context) {
        context.getStore(NAMESPACE).put("iterations", new PropertyScope("1"));
    }

    @Override public void afterAll(ExtensionContext context) {
        var scope = context.getStore(NAMESPACE).remove("iterations", PropertyScope.class);
        if (scope != null) scope.close();
    }

    /** Also closed by JUnit's store if setup or teardown fails. */
    public static final class PropertyScope implements ExtensionContext.Store.CloseableResource, AutoCloseable {
        private final String previous = System.getProperty(PROPERTY);
        private boolean closed;

        public PropertyScope(String value) {
            if (value == null) System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY, value);
        }

        @Override public void close() {
            if (closed) return;
            if (previous == null) System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY, previous);
            closed = true;
        }
    }
}
