// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.google.common.collect.Maps;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PathMapper;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.sdk.PythonEnvUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class PydevConsoleRunnerFactory extends PythonConsoleRunnerFactory {

  protected static class ConsoleParameters {
    @NotNull Project project;
    @NotNull Sdk sdk;
    @Nullable String workingDir;
    @NotNull Map<String, String> envs;
    @NotNull PyConsoleType consoleType;
    @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider;
    @NotNull Consumer<String> rerunAction;
    @NotNull String[] setupFragment;

    public ConsoleParameters(@NotNull Project project,
                             @NotNull Sdk sdk,
                             @Nullable String workingDir,
                             @NotNull Map<String, String> envs,
                             @NotNull PyConsoleType consoleType,
                             @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                             @NotNull Consumer<String> rerunAction,
                             @NotNull String[] setupFragment) {
      this.project = project;
      this.sdk = sdk;
      this.workingDir = workingDir;
      this.envs = envs;
      this.consoleType = consoleType;
      this.settingsProvider = settingsProvider;
      this.rerunAction = rerunAction;
      this.setupFragment = setupFragment;
    }
  }

  protected ConsoleParameters createConsoleParameters(@NotNull Project project,
                                                      @Nullable Module contextModule) {
    Pair<Sdk, Module> sdkAndModule = PydevConsoleRunner.findPythonSdkAndModule(project, contextModule);

    @Nullable Module module = sdkAndModule.second;
    Sdk sdk = sdkAndModule.first;

    assert sdk != null;

    PyConsoleOptions.PyConsoleSettings settingsProvider = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();

    PathMapper pathMapper = PydevConsoleRunner.getPathMapper(project, sdk, settingsProvider);

    String workingDir = getWorkingDir(project, module, pathMapper, settingsProvider);

    String[] setupFragment = createSetupFragment(module, workingDir, pathMapper, settingsProvider);

    Map<String, String> envs = Maps.newHashMap(settingsProvider.getEnvs());
    putIPythonEnvFlag(project, envs);

    Consumer<String> rerunAction = title -> {
      PydevConsoleRunner runner = createConsoleRunner(project, module);
      if (runner instanceof PydevConsoleRunnerImpl) {
        ((PydevConsoleRunnerImpl)runner).setConsoleTitle(title);
      }
      runner.run(true);
    };

    return new ConsoleParameters(project, sdk, workingDir, envs, PyConsoleType.PYTHON, settingsProvider, rerunAction, setupFragment);
  }

  @Override
  @NotNull
  public PydevConsoleRunner createConsoleRunner(@NotNull Project project,
                                                @Nullable Module contextModule) {
    final ConsoleParameters consoleParameters = createConsoleParameters(project, contextModule);
    return createConsoleRunner(project, consoleParameters.sdk, consoleParameters.workingDir, consoleParameters.envs,
                               consoleParameters.consoleType,
                               consoleParameters.settingsProvider, consoleParameters.rerunAction, consoleParameters.setupFragment);
  }

  public static void putIPythonEnvFlag(@NotNull Project project, Map<String, String> envs) {
    String ipythonEnabled = PyConsoleOptions.getInstance(project).isIpythonEnabled() ? "True" : "False";
    envs.put(PythonEnvUtil.IPYTHONENABLE, ipythonEnabled);
  }

  @Nullable
  public static String getWorkingDir(@NotNull Project project,
                                     @Nullable Module module,
                                     @Nullable PathMapper pathMapper,
                                     PyConsoleOptions.PyConsoleSettings settingsProvider) {
    String workingDir = settingsProvider.getWorkingDirectory();
    if (StringUtil.isEmpty(workingDir)) {
      if (module != null && ModuleRootManager.getInstance(module).getContentRoots().length > 0) {
        workingDir = ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();
      }
      else {
        if (ModuleManager.getInstance(project).getModules().length > 0) {
          VirtualFile[] roots = ModuleRootManager.getInstance(ModuleManager.getInstance(project).getModules()[0]).getContentRoots();
          if (roots.length > 0) {
            workingDir = roots[0].getPath();
          }
        }
      }
    }

    if (pathMapper != null && workingDir != null) {
      workingDir = pathMapper.convertToRemote(workingDir);
    }

    return workingDir;
  }

  public static String[] createSetupFragment(@Nullable Module module,
                                             @Nullable String workingDir,
                                             @Nullable PathMapper pathMapper,
                                             PyConsoleOptions.PyConsoleSettings settingsProvider) {
    String customStartScript = settingsProvider.getCustomStartScript();
    if (customStartScript.trim().length() > 0) {
      customStartScript = "\n" + customStartScript;
    }
    Collection<String> pythonPath = PythonCommandLineState.collectPythonPath(module, settingsProvider.shouldAddContentRoots(),
                                                                             settingsProvider.shouldAddSourceRoots());
    if (pathMapper != null) {
      pythonPath = pathMapper.convertToRemote(pythonPath);
    }
    String selfPathAppend = PydevConsoleRunner.constructPyPathAndWorkingDirCommand(pythonPath, workingDir, customStartScript);

    BuildoutFacet facet = null;
    if (module != null) {
      facet = BuildoutFacet.getInstance(module);
    }
    String[] setupFragment;
    if (facet != null) {
      List<String> path = facet.getAdditionalPythonPath();
      if (pathMapper != null) {
        path = pathMapper.convertToRemote(path);
      }
      String prependStatement = facet.getPathPrependStatement(path);
      setupFragment = new String[]{prependStatement, selfPathAppend};
    }
    else {
      setupFragment = new String[]{selfPathAppend};
    }

    return setupFragment;
  }

  @NotNull
  protected PydevConsoleRunner createConsoleRunner(@NotNull Project project,
                                                   @NotNull Sdk sdk,
                                                   @Nullable String workingDir,
                                                   @NotNull Map<String, String> envs,
                                                   @NotNull PyConsoleType consoleType,
                                                   @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                                   @NotNull Consumer<String> rerunAction,
                                                   @NotNull String... setupFragment) {
    return new PydevConsoleRunnerImpl(project, sdk, consoleType, workingDir, envs, settingsProvider, rerunAction, setupFragment);
  }

  @Override
  @NotNull
  public PydevConsoleRunner createConsoleRunnerWithFile(@NotNull Project project,
                                                        @Nullable Module contextModule,
                                                        @Nullable String runFileText,
                                                        @NotNull PythonRunConfiguration config) {
    final ConsoleParameters consoleParameters = createConsoleParameters(project, contextModule);

    Consumer<String> rerunAction = title -> {
      PydevConsoleRunner runner = createConsoleRunnerWithFile(project, contextModule, runFileText, config);
      if (runner instanceof PydevConsoleRunnerImpl) {
        ((PydevConsoleRunnerImpl)runner).setConsoleTitle(title);
      }
      final PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(project);
      runner.addConsoleListener(new PydevConsoleRunner.ConsoleListener() {
        @Override
        public void handleConsoleInitialized(@NotNull LanguageConsoleView consoleView) {
          if (consoleView instanceof PyCodeExecutor) {
            ((PyCodeExecutor)consoleView).executeCode(runFileText, null);
            if (toolWindow != null) {
              toolWindow.getToolWindow().show(null);
            }
          }
        }
      });
      runner.run(true);
    };
    Sdk sdk = config.getSdk() != null ? config.getSdk() : consoleParameters.sdk;
    String workingDir = config.getWorkingDirectory() != null ? config.getWorkingDirectory() : consoleParameters.workingDir;

    return new PydevConsoleWithFileRunnerImpl(project, sdk, consoleParameters.consoleType, config.getName(), workingDir,
                                              consoleParameters.envs, consoleParameters.settingsProvider, rerunAction, config,
                                              consoleParameters.setupFragment);
  }
}
