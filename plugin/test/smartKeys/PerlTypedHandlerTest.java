/*
 * Copyright 2015-2019 Alexandr Evstigneev
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

package smartKeys;


import com.perl5.lang.perl.idea.codeInsight.Perl5CodeInsightSettings;
import editor.PerlSmartKeysTestCase;
import org.junit.Test;
public class PerlTypedHandlerTest extends PerlSmartKeysTestCase {

  @Override
  protected String getBaseDataPath() {
    return "testData/smartKeys/perl";
  }

  @Test
  public void testSmartHashLonger() {
    doTestSmartCommaSequence();
  }

  @Test
  public void testSmartHashShorter() {
    doTestSmartCommaSequence();
  }

  @Test
  public void testSmartHashDisabled() {
    doTestSmartCommaSequence(false);
  }

  private void doTestSmartCommaSequence() {
    doTestSmartCommaSequence(true);
  }

  private void doTestSmartCommaSequence(boolean enabled) {
    Perl5CodeInsightSettings.getInstance().SMART_COMMA_SEQUENCE_TYPING = enabled;
    doTest(" ");
  }

  @Test
  public void testDoubleColonInUseEnabled() {
    Perl5CodeInsightSettings.getInstance().AUTO_INSERT_COLON = true;
    doTest("use Mojolicious<caret>", ":", "use Mojolicious::<caret>");
  }

  @Test
  public void testDoubleColonInUseDisabled() {
    Perl5CodeInsightSettings.getInstance().AUTO_INSERT_COLON = false;
    doTest("use Mojolicious<caret>", ":", "use Mojolicious:<caret>");
  }

  @Test
  public void testExtraDoubleColonInUseEnabled() {
    Perl5CodeInsightSettings.getInstance().AUTO_INSERT_COLON = true;
    doTest("use Mojolicious::<caret>", ":", "use Mojolicious::<caret>");
  }

  @Test
  public void testExtraDoubleColonInUseDisabled() {
    Perl5CodeInsightSettings.getInstance().AUTO_INSERT_COLON = false;
    doTest("use Mojolicious::<caret>", ":", "use Mojolicious:::<caret>");
  }
}
