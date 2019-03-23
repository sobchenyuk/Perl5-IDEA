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

package com.perl5.lang.perl.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by hurricup on 08.08.2015.
 */
public interface PerlString extends PerlQuoted {
  /**
   * @return first element of string content. Or null if string is empty or invalid
   */
  PsiElement getFirstContentToken();

  /**
   * @return all children, including leaf ones
   */
  @NotNull
  default List<PsiElement> getAllChildrenList() {
    PsiElement run = getFirstContentToken();

    if (run == null) {
      return Collections.emptyList();
    }

    PsiElement closeQuote = getCloseQuoteElement();
    List<PsiElement> result = new ArrayList<>();
    while (run != null && run != closeQuote) {
      result.add(run);
      run = run.getNextSibling();
    }
    return result;
  }
}
