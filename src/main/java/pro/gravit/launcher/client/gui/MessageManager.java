package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.client.ClientLauncher;
import pro.gravit.utils.helper.JVMHelper;

import javax.swing.*;

public class MessageManager {
    public final JavaFXApplication application;

    public MessageManager(JavaFXApplication application) {
        this.application = application;
    }

    public void noJavaFxAlert()
    {
        if(application == null)
        {
            String message = String.format("Библиотеки JavaFX не найдены. У вас %s(x%d) ОС %s(x%d). Java %s. Установите Java с поддержкой JavaFX, например OracleJRE 8 x%d с официального сайта.\nЕсли вы не можете решить проблему самостоятельно обратитесь к администрации своего проекта" , JVMHelper.RUNTIME_MXBEAN.getVmName(),
                    JVMHelper.JVM_BITS, JVMHelper.OS_TYPE.name, JVMHelper.OS_BITS, JVMHelper.RUNTIME_MXBEAN.getSpecVersion(), JVMHelper.OS_BITS);
            JOptionPane.showMessageDialog(null, message, "GravitLauncher", JOptionPane.ERROR_MESSAGE);
        }
    }
}
