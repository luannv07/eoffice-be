package vn.edu.epu.eoffice_be.security.pki;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.lang.annotation.*;

/** Low-cost PKCS12 only for the explicitly annotated test classes. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(PkiIterationExtension.class)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
@Isolated("Test-scoped Bouncy Castle system property")
@Execution(ExecutionMode.SAME_THREAD)
public @interface FastPkiTest { }
