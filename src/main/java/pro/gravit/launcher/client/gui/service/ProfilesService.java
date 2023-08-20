package pro.gravit.launcher.client.gui.service;

import com.google.gson.reflect.TypeToken;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.gui.scenes.options.OptionsScene;
import pro.gravit.launcher.events.request.ProfilesRequestEvent;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.optional.OptionalFile;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfilesService {
    private List<ClientProfile> profiles;
    private ClientProfile profile;
    private Map<ClientProfile, OptionalView> optionalViewMap;

    public ProfilesService() {
    }

    public Map<ClientProfile, OptionalView> getOptionalViewMap() {
        return optionalViewMap;
    }

    public OptionalView getOptionalView() {
        return this.optionalViewMap.get(this.profile);
    }

    public OptionalView getOptionalView(ClientProfile profile) {
        return this.optionalViewMap.get(profile);
    }

    public void setOptionalView(ClientProfile profile, OptionalView view) {
        optionalViewMap.put(profile, view);
    }

    public void setProfilesResult(ProfilesRequestEvent rawProfilesResult) {
        this.profiles = rawProfilesResult.profiles;
        this.profiles.sort(ClientProfile::compareTo);
        if (this.optionalViewMap == null) this.optionalViewMap = new HashMap<>();
        for (ClientProfile profile : profiles) {
            OptionalView oldView = this.optionalViewMap.get(profile);
            OptionalView newView = oldView != null ? new OptionalView(profile, oldView) : new OptionalView(profile);
            this.optionalViewMap.put(profile, newView);
        }
    }

    public List<ClientProfile> getProfiles() {
        return profiles;
    }

    public ClientProfile getProfile() {
        return profile;
    }

    public void setProfile(ClientProfile profile) {
        this.profile = profile;
    }


    public void saveAll() throws IOException {
        if (profiles == null) return;
        Path optionsFile = DirBridge.dir.resolve("options.json");
        List<OptionsScene.OptionalListEntry> list = new ArrayList<>(5);
        for (ClientProfile clientProfile : profiles) {
            OptionsScene.OptionalListEntry entry = new OptionsScene.OptionalListEntry();
            entry.name = clientProfile.getTitle();
            entry.profileUUID = clientProfile.getUUID();
            OptionalView view = optionalViewMap.get(clientProfile);
            view.all.forEach((optionalFile -> {
                if (optionalFile.visible) {
                    boolean isEnabled = view.enabled.contains(optionalFile);
                    OptionalView.OptionalFileInstallInfo installInfo = view.installInfo.get(optionalFile);
                    entry.enabled.add(new OptionsScene.OptionalListEntryPair(optionalFile, isEnabled, installInfo));
                }
            }));
            list.add(entry);
        }
        try (Writer writer = IOHelper.newWriter(optionsFile)) {
            Launcher.gsonManager.gson.toJson(list, writer);
        }
    }

    public void loadAll() throws IOException {
        if (profiles == null) return;
        Path optionsFile = DirBridge.dir.resolve("options.json");
        if (!Files.exists(optionsFile)) return;

        Type collectionType = new TypeToken<List<OptionsScene.OptionalListEntry>>() {
        }.getType();

        try (Reader reader = IOHelper.newReader(optionsFile)) {
            List<OptionsScene.OptionalListEntry> list = Launcher.gsonManager.gson.fromJson(reader, collectionType);
            for (OptionsScene.OptionalListEntry entry : list) {
                ClientProfile selectedProfile = null;
                for (ClientProfile clientProfile : profiles) {
                    if (entry.profileUUID != null
                            ? entry.profileUUID.equals(clientProfile.getUUID())
                            : clientProfile.getTitle().equals(entry.name)) selectedProfile = clientProfile;
                }
                if (selectedProfile == null) {
                    LogHelper.warning("Optional: profile %s(%s) not found", entry.name, entry.profileUUID);
                    continue;
                }
                OptionalView view = optionalViewMap.get(selectedProfile);
                for (OptionsScene.OptionalListEntryPair entryPair : entry.enabled) {
                    try {
                        OptionalFile file = selectedProfile.getOptionalFile(entryPair.name);
                        if (file.visible) {
                            if (entryPair.mark)
                                view.enable(file, entryPair.installInfo != null
                                        && entryPair.installInfo.isManual, null);
                            else view.disable(file, null);
                        }
                    } catch (Exception exc) {
                        LogHelper.warning("Optional: in profile %s markOptional mod %s failed",
                                          selectedProfile.getTitle(), entryPair.name);
                    }
                }
            }
        }
    }
}