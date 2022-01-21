package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author LeeWyatt
 */
public interface PluginIcons {

    //1. TODO 设置好这个 , 以及_dark
    Icon FXToolsToolWindow = IconLoader.getIcon("/icons/fx_tools_tool_window.svg", PluginIcons.class);
    Icon Transparent = IconLoader.getIcon("/icons/transparent_bg.svg", PluginIcons.class);
    Icon Data = IconLoader.getIcon("/icons/data.svg",PluginIcons.class);
    Icon Clear = IconLoader.getIcon("/icons/clear.svg",PluginIcons.class);
    Icon Other = IconLoader.getIcon("/icons/other.svg",PluginIcons.class);

}
