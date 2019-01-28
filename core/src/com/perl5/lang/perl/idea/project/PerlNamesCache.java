/*
 * Copyright 2015-2017 Alexandr Evstigneev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.perl5.lang.perl.idea.project;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.perl5.lang.perl.util.PerlGlobUtil;
import com.perl5.lang.perl.util.PerlPackageUtil;
import com.perl5.lang.perl.util.PerlSubUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Created by hurricup on 04.09.2015.
 */
public class PerlNamesCache implements ProjectComponent {
  private final NamesCacheUpdater myUpdaterRunner = new NamesCacheUpdater();
  private final Thread myUpdaterThread = new Thread(myUpdaterRunner);
  private final Project myProject;
  private Set<String> myKnownSubs = new THashSet<>();
  private Set<String> myKnownPackages = new THashSet<>();
  private final Runnable myCacheUpdaterWorker = new Runnable() {
    @Override
    public void run() {
      DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
        Set<String> newSet = new THashSet<>();
        newSet.addAll(PerlSubUtil.getDeclaredSubsNames(myProject));
        newSet.addAll(PerlSubUtil.getDefinedSubsNames(myProject));
        newSet.addAll(PerlGlobUtil.getDefinedGlobsNames(myProject));
        myKnownSubs = newSet;

        newSet = new THashSet<>();
        newSet.addAll(PerlPackageUtil.CORE_PACKAGES_ALL);
        newSet.addAll(PerlPackageUtil.getDefinedPackageNames(myProject));
        myKnownPackages = newSet;
      });
    }
  };
  //	long notifyCounter = 0;
  private boolean isNotified = false;

  public PerlNamesCache(Project project) {
    this.myProject = project;
  }

  @Override
  public void projectOpened() {

  }

  @Override
  public void projectClosed() {

  }

  public void forceCacheUpdate() {
    myCacheUpdaterWorker.run();
  }

  @Override
  public void initComponent() {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(myUpdaterThread::start);
  }

  @Override
  public void disposeComponent() {
    myUpdaterRunner.stopUpdater();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "Perl5 names cache";
  }

  public Set<String> getSubsNamesSet() {
    myUpdaterRunner.update();
    return myKnownSubs;
  }

  public Set<String> getPackagesNamesSet() {
    myUpdaterRunner.update();
    return myKnownPackages;
  }

  @NotNull
  public static PerlNamesCache getInstance(@NotNull Project project) {
    return project.getComponent(PerlNamesCache.class);
  }

  protected class NamesCacheUpdater implements Runnable {
    private static final long TTL = 1000;
    private boolean stopThis = false;
    private long lastUpdate = 0;

    @Override
    public void run() {

      while (!stopThis) {
        try {
          myCacheUpdaterWorker.run();
        }
        catch (ProcessCanceledException ignore) {
        }

        lastUpdate = System.currentTimeMillis();
        isNotified = false;

        synchronized (this) {
          try {
            wait();
          }
          catch (Exception e) {
            break;
          }
        }
      }
    }

    public void update() {
      if (!isNotified && lastUpdate + TTL < System.currentTimeMillis()) {
        synchronized (this) {
          isNotified = true;
          notify();
        }
      }
    }

    public void stopUpdater() {
      stopThis = true;
      synchronized (this) {
        notify();
      }
    }
  }
}