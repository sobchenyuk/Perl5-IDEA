/*
 * Copyright 2015-2020 Alexandr Evstigneev
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

package com.perl5.lang.perl.parser.moose.psi.impl;

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.perl5.lang.perl.idea.codeInsight.typeInference.value.PerlScalarValue;
import com.perl5.lang.perl.idea.codeInsight.typeInference.value.PerlSmartGetterValue;
import com.perl5.lang.perl.idea.codeInsight.typeInference.value.PerlValue;
import com.perl5.lang.perl.idea.codeInsight.typeInference.value.PerlValuesManager;
import com.perl5.lang.perl.psi.*;
import com.perl5.lang.perl.psi.impl.PerlSubCallElement;
import com.perl5.lang.perl.psi.light.PerlDelegatingLightNamedElement;
import com.perl5.lang.perl.psi.light.PerlLightMethodDefinitionElement;
import com.perl5.lang.perl.psi.stubs.calls.PerlSubCallElementStub;
import com.perl5.lang.perl.psi.stubs.subsdefinitions.PerlSubDefinitionStub;
import com.perl5.lang.perl.psi.utils.PerlResolveUtil;
import com.perl5.lang.perl.psi.utils.PerlSubAnnotations;
import com.perl5.lang.perl.psi.utils.PerlSubArgument;
import com.perl5.lang.perl.util.PerlArrayUtil;
import com.perl5.lang.perl.util.PerlHashEntry;
import com.perl5.lang.perl.util.PerlHashUtil;
import com.perl5.lang.perl.util.PerlPackageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.perl5.lang.perl.psi.stubs.PerlStubElementTypes.LIGHT_ATTRIBUTE_DEFINITION;
import static com.perl5.lang.perl.psi.stubs.PerlStubElementTypes.LIGHT_METHOD_DEFINITION;

public class PerlMooseAttributeHandler extends PerlSubCallHandlerWithEmptyData {
  private static final String MUTATOR_KEY = "writer";
  private static final String ACCESSOR_KEY = "accessor";
  private static final String READER_KEY = "reader";
  private static final List<String> MOOSE_SUB_NAMES_KEYS = Arrays.asList(
    READER_KEY, MUTATOR_KEY, ACCESSOR_KEY, "predicate", "clearer"
  );

  @NotNull
  @Override
  public List<? extends PerlDelegatingLightNamedElement<?>> computeLightElementsFromPsi(@NotNull PerlSubCallElement subCallElement) {
    Pair<List<PsiElement>, List<PsiElement>> lists = getIdentifiersAndListElements(subCallElement);
    if (lists == null) {
      return Collections.emptyList();
    }
    return lists.second.size() < 3 ?
           createMojoAttributes(subCallElement, lists) : createMooseAttributes(subCallElement, lists.first, lists.second);
  }

  @NotNull
  @Override
  public List<? extends PerlDelegatingLightNamedElement<?>> computeLightElementsFromStubs(@NotNull PerlSubCallElement psiElement,
                                                                                          @NotNull PerlSubCallElementStub stubElement) {
    return stubElement.getLightNamedElementsStubs().stream()
      .map(childStub -> {
        IStubElementType<?, ?> stubType = childStub.getStubType();
        if (stubType == LIGHT_METHOD_DEFINITION) {
          return new PerlLightMethodDefinitionElement<>(psiElement, (PerlSubDefinitionStub)childStub);
        }
        else if (stubType == LIGHT_ATTRIBUTE_DEFINITION) {
          return new PerlAttributeDefinition(psiElement, (PerlSubDefinitionStub)childStub);
        }
        throw new IllegalArgumentException("Unexpected stubElement type: " + stubType);
      })
      .collect(Collectors.toList());
  }

  /**
   * @return pair of lists: identifiers lists and list of has arguments
   * null if something went wrong
   */
  @Nullable
  private Pair<List<PsiElement>, List<PsiElement>> getIdentifiersAndListElements(@NotNull PerlSubCallElement subCallElement) {
    PsiPerlCallArguments arguments = subCallElement.getCallArguments();
    if (arguments == null) {
      return null;
    }
    List<PsiElement> listElements = PerlArrayUtil.collectListElements(arguments.getFirstChild());
    if (listElements.isEmpty()) {
      return null;
    }
    PsiElement namesContainer = listElements.get(0);
    if (namesContainer instanceof PsiPerlAnonArray) {
      namesContainer = ((PsiPerlAnonArray)namesContainer).getExpr();
    }
    List<PsiElement> identifiers = ContainerUtil.filter(PerlArrayUtil.collectListElements(namesContainer),
                                                        subCallElement::isAcceptableIdentifierElement);
    if (identifiers.isEmpty()) {
      return null;
    }
    return Pair.create(identifiers, listElements);
  }

  @NotNull
  private List<PerlDelegatingLightNamedElement<?>> createMojoAttributes(@NotNull PerlSubCallElement subCallElement,
                                                                        @NotNull Pair<List<PsiElement>, List<PsiElement>> lists) {
    List<PsiElement> arguments = lists.second.subList(1, lists.second.size());
    PsiElement argument = ContainerUtil.getFirstItem(arguments);
    PerlSubExpr subExpr = ObjectUtils.tryCast(argument, PerlSubExpr.class);

    List<PerlDelegatingLightNamedElement<?>> result = new ArrayList<>();
    String namespaceName = PerlPackageUtil.getContextNamespaceName(subCallElement);
    for (PsiElement identifier : lists.first) {
      AtomicNotNullLazyValue<PerlValue> valueProvider = AtomicNotNullLazyValue.createValue(() -> PerlSmartGetterValue.create(
        subExpr != null ? PerlResolveUtil.computeReturnValueFromControlFlow(subExpr) :
        argument != null ? PerlValuesManager.from(argument) : PerlScalarValue.create(namespaceName)));
      PerlLightMethodDefinitionElement<PerlSubCallElement> newMethod = new PerlLightMethodDefinitionElement<>(
        subCallElement,
        ElementManipulators.getValueText(identifier),
        LIGHT_METHOD_DEFINITION,
        identifier,
        namespaceName,
        Arrays.asList(PerlSubArgument.self(), PerlSubArgument.optionalScalar("new_value")),
        PerlSubAnnotations.tryToFindAnnotations(identifier, subCallElement.getParent()),
        valueProvider,
        subExpr == null ? null : subExpr.getBlock()
      );
      result.add(newMethod);
    }

    return result;
  }

  @NotNull
  public List<String> getAttributesNames(@NotNull PerlSubCallElement subCallElement) {
    // fixme we have no stubs here
    /*
    PerlMooseAttributeWrapperStub stub = getGreenStub();
    if (stub != null) {
      return stub.getAttributesNames();
    }
    */
    Pair<List<PsiElement>, List<PsiElement>> lists = getIdentifiersAndListElements(subCallElement);
    if (lists == null || lists.first.isEmpty()) {
      return Collections.emptyList();
    }
    return lists.first.stream().map(ElementManipulators::getValueText).collect(Collectors.toList());
  }

  @NotNull
  private List<PerlDelegatingLightNamedElement<?>> createMooseAttributes(@NotNull PerlSubCallElement subCallElement,
                                                                         @NotNull List<PsiElement> identifiers,
                                                                         @NotNull List<PsiElement> listElements) {

    List<PerlDelegatingLightNamedElement<?>> result = new ArrayList<>();
    String packageName = PerlPackageUtil.getContextNamespaceName(subCallElement);

    Map<String, PerlHashEntry> parameters = PerlHashUtil.packToHash(listElements.subList(1, listElements.size()));
    // handling is
    PerlHashEntry isParameter = parameters.get("is");
    boolean isWritable = isParameter != null && StringUtil.equals("rw", isParameter.getValueString());
    PsiElement forcedIdentifier = null;

    // handling isa and does
    PerlHashEntry isaEntry = parameters.get("isa");
    String valueClass = null;
    if (isaEntry == null) {
      isaEntry = parameters.get("does");
    }

    if (isaEntry != null && isaEntry.valueElement != null && subCallElement.isAcceptableIdentifierElement(isaEntry.valueElement)) {
      valueClass = isaEntry.getValueString();
      if (StringUtil.isEmpty(valueClass)) {
        valueClass = null;
      }
    }

    // handling accessor, reader, etc.
    List<PerlLightMethodDefinitionElement<?>> secondaryResult = new ArrayList<>();
    for (String key : MOOSE_SUB_NAMES_KEYS) {
      PerlHashEntry entry = parameters.get(key);
      if (entry == null) {
        continue;
      }

      String methodName = entry.getValueString();
      if (StringUtil.isEmpty(methodName)) {
        continue;
      }

      if (!isWritable && key.equals(MUTATOR_KEY)) {
        continue;
      }

      if (!isWritable && key.equals(READER_KEY) || key.equals(ACCESSOR_KEY)) {
        forcedIdentifier = entry.valueElement;
        continue;
      }

      if (!subCallElement.isAcceptableIdentifierElement(entry.valueElement)) {
        continue;
      }

      // fixme we could optimize new_value with subclassing and hardcoding of signature
      PsiElement identifier = entry.getNonNullValueElement();
      PerlLightMethodDefinitionElement<PerlSubCallElement> secondaryElement = new PerlLightMethodDefinitionElement<>(
        subCallElement,
        ElementManipulators.getValueText(identifier),
        LIGHT_METHOD_DEFINITION,
        identifier,
        packageName,
        key.equals(MUTATOR_KEY) ? Arrays.asList(PerlSubArgument.self(), PerlSubArgument.optionalScalar("new_value", valueClass))
                                : Collections.emptyList(),
        PerlSubAnnotations.tryToFindAnnotations(identifier, entry.keyElement, subCallElement.getParent())
      );

      if (key.equals(READER_KEY) && valueClass != null) {
        secondaryElement.setReturnValueFromCode(PerlScalarValue.create(valueClass));
      }

      secondaryResult.add(
        secondaryElement
      );
    }

    // handle handles
    PerlHashEntry handlesEntry = parameters.get("handles");
    if (handlesEntry != null) {
      // to show proper signatures, we need an access to delegates, what requires indexes; we should do this in runtime, not indexing, but store delegation target
      if (handlesEntry.valueElement instanceof PsiPerlAnonHash) {
        // handle handles HASH
        Map<String, PerlHashEntry> delegatesMap = PerlHashUtil.collectHashMap(handlesEntry.valueElement);
        for (PerlHashEntry delegateEntry : delegatesMap.values()) {
          if (!subCallElement.isAcceptableIdentifierElement(delegateEntry.keyElement)) {
            continue;
          }
          secondaryResult.add(new PerlLightMethodDefinitionElement<>(
            subCallElement,
            ElementManipulators.getValueText(delegateEntry.keyElement),
            LIGHT_METHOD_DEFINITION,
            delegateEntry.keyElement,
            packageName,
            Collections.emptyList(),
            PerlSubAnnotations.tryToFindAnnotations(delegateEntry.keyElement, handlesEntry.keyElement, subCallElement.getParent())
          ));
        }
      }
      else if (handlesEntry.valueElement instanceof PsiPerlAnonArray) {
        // handle handles ARRAY
        List<PsiElement> delegatesIdentifiers = PerlArrayUtil.collectListElements(((PsiPerlAnonArray)handlesEntry.valueElement).getExpr());
        for (PsiElement identifier : delegatesIdentifiers) {
          if (!subCallElement.isAcceptableIdentifierElement(identifier)) {
            continue;
          }
          secondaryResult.add(new PerlLightMethodDefinitionElement<>(
            subCallElement,
            ElementManipulators.getValueText(identifier),
            LIGHT_METHOD_DEFINITION,
            identifier,
            packageName,
            Collections.emptyList(),
            PerlSubAnnotations.tryToFindAnnotations(identifier, handlesEntry.keyElement, subCallElement.getParent())
          ));
        }
      }
    }

    // handle required this should alter consttructor, no idea how for now
    // handle HANDLES ROLE OR ROLETYPE requires index
    // handle HANDLES DUCKTYPE requires index
    // handle HANDLES REGEXP this requires regexp & resolve

    for (PsiElement identifier : identifiers) {
      if (forcedIdentifier != null) {
        identifier = forcedIdentifier;
      }
      if (!subCallElement.isAcceptableIdentifierElement(identifier)) {
        continue;
      }
      PerlAttributeDefinition newElement = new PerlAttributeDefinition(
        subCallElement,
        PerlAttributeDefinition.DEFAULT_NAME_COMPUTATION.fun(ElementManipulators.getValueText(identifier)),
        LIGHT_ATTRIBUTE_DEFINITION,
        identifier,
        packageName,
        isWritable
        ? Arrays.asList(PerlSubArgument.self(), PerlSubArgument.optionalScalar("new_value", valueClass))
        : Collections.emptyList(),
        PerlSubAnnotations.tryToFindAnnotations(identifier, subCallElement.getParent())
      );
      if (valueClass != null) {
        PerlValue returnValue = PerlScalarValue.create(valueClass);
        newElement.setReturnValueFromCode(returnValue);
      }
      result.add(newElement);
      result.addAll(secondaryResult);
      secondaryResult.clear();
    }

    return result;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean isMooseAttributeWrapper(@Nullable PsiElement psiElement) {
    return psiElement instanceof PerlSubCallElement &&
           ((PerlSubCallElement)psiElement).getHandler() instanceof PerlMooseAttributeHandler;
  }

  public static PerlMooseAttributeHandler notNullFrom(@NotNull PerlSubCallElement subCallElement) {
    PerlSubCallHandler<?> provider = subCallElement.getHandler();
    assert provider instanceof PerlMooseAttributeHandler : "Got wrong provider: " + provider;
    return ((PerlMooseAttributeHandler)provider);
  }
}
