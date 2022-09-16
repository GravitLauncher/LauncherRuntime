package pro.gravit.launcher.client.gui.utils;

import pro.gravit.utils.enfs.dir.FileEntry;
import pro.gravit.utils.helper.SecurityHelper;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

public class RuntimeCryptedFile extends FileEntry {
    private final Supplier<InputStream> inputStream;
    private final String alg;
    private final SecretKeySpec sKeySpec;
    private final IvParameterSpec iKeySpec;

    public RuntimeCryptedFile(Supplier<InputStream> inputStream, byte[] key) {
        this.inputStream = inputStream;
        this.alg = "AES/CBC/PKCS5Padding";
        try {
            byte[] compat = SecurityHelper.getAESKey(key);
            sKeySpec = new SecretKeySpec(compat, "AES");
            iKeySpec = new IvParameterSpec("8u3d90ikr7o67lsq".getBytes());
        } catch (Exception e) {
            throw new SecurityException(e);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(alg);
            cipher.init(Cipher.DECRYPT_MODE, sKeySpec, iKeySpec);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new IOException(e);
        }
        return new CipherInputStream(inputStream.get(), cipher);
    }
}
