package pro.gravit.launcher.client;

import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.events.ClientEngineInitPhase;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.client.events.ClientPreGuiPhase;
import pro.gravit.launcher.gui.RuntimeProvider;
import pro.gravit.launcher.modules.LauncherInitContext;
import pro.gravit.launcher.modules.LauncherModule;
import pro.gravit.launcher.modules.LauncherModuleInfo;
import pro.gravit.utils.Version;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

import javax.swing.*;

public class JavaRuntimeModule extends LauncherModule {

    public final static String RUNTIME_NAME = "stdruntime";
    static LauncherEngine engine;
    private RuntimeProvider provider;

    public JavaRuntimeModule() {
        super(new LauncherModuleInfo("StdJavaRuntime", new Version(2, 0, 0, 1, Version.Type.DEV),
                0, new String[]{}, new String[]{"runtime"}));
    }

    private static void noJavaFxAlert() {
        String message = String.format("Библиотеки JavaFX не найдены. У вас %s(x%d) ОС %s(x%d). Java %s. Установите Java с поддержкой JavaFX, например OracleJRE 8 x%d с официального сайта.\n%s\nЕсли вы не можете решить проблему самостоятельно обратитесь к администрации своего проекта", JVMHelper.RUNTIME_MXBEAN.getVmName(),
                JVMHelper.JVM_BITS, JVMHelper.OS_TYPE.name, JVMHelper.OS_BITS, JVMHelper.RUNTIME_MXBEAN.getSpecVersion(), JVMHelper.OS_BITS, JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE ? "Убедитесь что вы запускаете с правильной Java(ПКМ->Открыть с помощью->Java 8)" : "Убедитесь, что по умолчанию стоит запуск с подходяшей Java и установлен openjfx той же версии");
        JOptionPane.showMessageDialog(null, message, "GravitLauncher", JOptionPane.ERROR_MESSAGE);
    }

    public static void noLocaleAlert(String file) {
        String message = String.format("Не найден файл %s при инициализации GUI. Дальнейшая работа невозможна.\nУбедитесь что все файлы дизайна лаунчера присутствуют в папке runtime при сборке лаунчера", file);
        JOptionPane.showMessageDialog(null, message, "GravitLauncher", JOptionPane.ERROR_MESSAGE);
    }

    public static void errorHandleAlert(Throwable e)
    {
        String message = String.format("Произошла серьезная ошибка при инициализации интерфейса лаунчера.\nДля пользователей:\nОбратитесь к администрации своего проекта с скриншотом этого окна\nJava %d (x%d) Ошибка %s\nОписание: %s\nБолее подробную информацию можно получить из лога", JVMHelper.JVM_VERSION, JVMHelper.JVM_BITS, e.getClass().getName(), e.getMessage() == null ? "null" : e.getMessage());
        JOptionPane.showMessageDialog(null, message, "GravitLauncher", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void init(LauncherInitContext initContext) {
        registerEvent(this::preGuiPhase, ClientPreGuiPhase.class);
        registerEvent(this::engineInitPhase, ClientEngineInitPhase.class);
        registerEvent(this::exitPhase, ClientExitPhase.class);
    }

    private void preGuiPhase(ClientPreGuiPhase phase) {
        try {
            Class.forName("javafx.application.Application");
        } catch (ClassNotFoundException e) {
            noJavaFxAlert();
            LauncherEngine.exitLauncher(0);
        }
        provider = new StdJavaRuntimeProvider();
        phase.runtimeProvider = provider;
    }

    private void engineInitPhase(ClientEngineInitPhase initPhase) {
        JavaRuntimeModule.engine = initPhase.engine;
    }

    private void exitPhase(ClientExitPhase exitPhase) {
        if (provider != null && provider instanceof StdJavaRuntimeProvider) {
            try {
                ((StdJavaRuntimeProvider) provider).getApplication().saveSettings();
            } catch (Throwable e) {
                LogHelper.error(e);
            }
        }
    }
}
