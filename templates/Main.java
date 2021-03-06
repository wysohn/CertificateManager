package io.github.wysohn.%pluginname%.main;

import io.github.wysohn.rapidframework3.bukkit.main.AbstractBukkitPlugin;
import io.github.wysohn.rapidframework3.core.command.SubCommand;
import io.github.wysohn.rapidframework3.core.inject.module.LanguagesModule;
import io.github.wysohn.rapidframework3.core.inject.module.ManagerModule;
import io.github.wysohn.rapidframework3.core.inject.module.MediatorModule;
import io.github.wysohn.rapidframework3.core.main.PluginMainBuilder;
import io.github.wysohn.rapidframework3.core.player.AbstractPlayerWrapper;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class $pluginname$ extends AbstractBukkitPlugin {
    public $pluginname$() {
    }

    private $pluginname$(JavaPluginLoader mockLoader) {
        super(mockLoader);
    }

    @Override
    protected void init(PluginMainBuilder pluginMainBuilder) {
        pluginMainBuilder.addModule(new LanguagesModule(PluginTempLangs.values()));
        pluginMainBuilder.addModule(new ManagerModule(
                //TODO your managers
        ));
        pluginMainBuilder.addModule(new MediatorModule(
                //TODO your mediators
        ));
        //TODO and some other modules as your need...
    }

    @Override
    protected void registerCommands(List<SubCommand> list) {
        //TODO register commands
    }

    @Override
    protected Optional<? extends AbstractPlayerWrapper> getPlayerWrapper(UUID uuid) {
        throw new RuntimeException("Need wrapper.");
    }
}
