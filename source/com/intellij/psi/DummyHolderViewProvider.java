/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.lang.Language;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.testFramework.MockVirtualFile;
import com.intellij.util.LocalTimeCounter;

import java.util.Set;
import java.util.Collections;

public class DummyHolderViewProvider implements FileViewProvider{
  private DummyHolder myHolder;
  private PsiManager myManager;
  private final long myModificationStamp;
  final MockVirtualFile myMockVirtualFile = new MockVirtualFile(myHolder != null ? myHolder.getName() : "DummyHolder", ""){
    protected CharSequence getContent() {
      return DummyHolderViewProvider.this.getContents();
    }
  };

  public DummyHolderViewProvider(final PsiManager manager) {
    myManager = manager;
    myModificationStamp = LocalTimeCounter.currentTime();
  }

  public PsiManager getManager() {
    return myManager;
  }

  @Nullable
  public Document getDocument() {
    return FileDocumentManager.getInstance().getDocument(getVirtualFile());
  }

  @NotNull
  public CharSequence getContents() {
    return myHolder != null ? myHolder.getText() : "";
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myMockVirtualFile;
  }

  public Language getBaseLanguage() {
    return myHolder.getLanguage();
  }

  public Set<Language> getRelevantLanguages() {
    return Collections.singleton(getBaseLanguage());
  }

  public PsiFile getPsi(Language target) {
    ((PsiManagerImpl)myManager).getFileManager().setViewProvider(getVirtualFile(), this);
    return target == getBaseLanguage() ? myHolder : null;
  }

  public void contentsChanged() {
  }

  public boolean isEventSystemEnabled() {
    return false;
  }

  public boolean isPhysical() {
    return false;
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void rootChanged(PsiFile psiFile) {
  }

  public void setDummyHolder(final DummyHolder dummyHolder) {
    myHolder = dummyHolder;
  }

  public FileViewProvider clone(){
    throw new RuntimeException("Clone is not supported for DummyHolderProviders. Use DummyHolder clone directly.");
  }

}
