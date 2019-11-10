package pro.gravit.launcher.client.gui;

import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

public class GuiClassLoader extends ClassLoader {
    public GuiClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public URL getResource(String name) {
        LogHelper.debug("getResource %s", name);
        return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        LogHelper.debug("getResource %s", name);
        return super.getResources(name);
    }

    @Override
    protected URL findResource(String name) {
        LogHelper.debug("findResource %s", name);
        return super.findResource(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        LogHelper.debug("findResource %s", name);
        return super.findResources(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        LogHelper.debug("getResourceAsStream %s", name);
        return super.getResourceAsStream(name);
    }
}
