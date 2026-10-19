package vn.edu.epu.eoffice_be.security.pki;

/** A signing field for this user already exists in the PDF. */
public class DuplicateSignatureException extends IllegalStateException {
    public DuplicateSignatureException(String message) {
        super(message);
    }
}
