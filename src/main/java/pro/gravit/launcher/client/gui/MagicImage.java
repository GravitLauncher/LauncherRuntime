package pro.gravit.launcher.client.gui;

import javafx.beans.NamedArg;
import javafx.scene.image.Image;
import pro.gravit.utils.helper.LogHelper;

public class MagicImage extends Image {
    public MagicImage(@NamedArg("url") String s) {
        super(s);
        LogHelper.info("MagicImageCreated %s", s);
    }

}
