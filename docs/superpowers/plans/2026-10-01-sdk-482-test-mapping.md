# SDK-482 Test Mapping (Task 1 gate document)

Working document of `2026-10-01-sdk-482-remove-deprecated-code.md`; deleted together with the plan.
Produced on 2026-10-02 from the branch state `SDK-375` (HEAD `0fb022bd` plus the untracked SDK-482 docs and
`ExceptionSupport.java`). No source file was changed to produce it.

## 1. Baseline

`mvn test -pl njams-sdk` (before any SDK-482 change):

**Tests run: 1567, Failures: 0, Errors: 0, Skipped: 2 - BUILD SUCCESS**

The two skipped tests were already skipped before: `communication.jms.AMQInitTest` (1) and
`configuration.provider.FileConfigurationProviderTest` (1).

## 2. How the list was produced

1. All 194 test sources of `njams-sdk` were compiled with `javac -Xlint:deprecation,removal -Xmaxwarns 100000`
   against the current `target/classes`. That gives 1152 warnings in 76 files; the noise that is out of scope
   (`new Integer`, `new URL`, `Assert.assertThat`, `JsonSerializerFactory` = section E, `SimpleProcessModelLayouter`,
   `setExclude`/`setDataMasking`/`PROPERTY_SERVER_COMPATIBILITY` = keep marks, `setStatus` = keep) was filtered out.
2. Tests that suppress the warning were found by `grep SuppressWarnings` and read by hand
   (`JobInstrumentedTest`, `ConfigurationPathOverloadsTest`, `ReceiverListenerDeregistrationSpecTest`,
   `NjamsTest` line 252, parts of `PathTest`).
3. Every warning was attributed to its enclosing test method. Every row below was checked against the test source;
   every "existing replacement test" named below was opened and its assertions compared.
4. Sample modules: compiled with `-Dmaven.compiler.showDeprecation=true` (see appendix A). `njams-sdk-communication-it`
   test sources: grep (appendix B).

Note: members that are only **narrowed** to package-private (`JobImpl.flush`, `timerFlush`, `getEstimatedSize`,
`addToEstimatedSize`, `limitLength`, `getNjams`, `setActivityErrorEvent`, `isActiveTracepoint`,
`getActivityConfiguration`, `isRecording`) need no test change when the test is in package
`com.im.njams.sdk.logmessage` (all of them are, except `NjamsSampleTest`, package `client`). Those hits are
listed as KEEP-UNCHANGED where they occur.

## 3. Legend

Actions:

| Action | Meaning |
|---|---|
| DELETE | Test (or group) covers only removed functionality, or an equivalent test is named in the replacement column. A DELETE row either names an existing replacement test that was verified, or states that the behaviour disappears with the feature. |
| REDUCE | Mixed test: only the assertions/lines that cover removed functionality are dropped; the kept assertions stay textually unchanged. |
| RETARGET-SETUP | A removed member is used in arrange/act code (or a Mockito stub); it is replaced by the facet call. No assertion expression is touched. |
| RETARGET-ARG | Input data of a kept test is converted (legacy `common.Path` object to new `Path`); expected values untouched. |
| RETARGET-OBSERVER (*) | The removed member is the observed value inside an `assert*`/`verify` expression of a **kept** behaviour and no equivalent facet test exists. The expected value stays, only the accessor changes (for example `job.getAttribute(k)` to `job.attributes().get(k)`). This is an assertion-text change; see section 6 for the policy question. |
| MIGRATE-SETUP | Only `Settings`/provider type in the test setup moves to `ClientSettings` (Task 9, no assertion touched). |
| KEEP-UNCHANGED | Member is only narrowed or the deprecation annotation vanishes; the test compiles and asserts as before. |

`n` = number of test methods in the row (for MIGRATE-SETUP: number of classes, see section 5.3).
Test names without a class prefix are in the class of the table heading.

Test-class abbreviations: NFAT = `NjamsFacetApiTest`, JFAT = `JobFacetApiTest`, JAT = `JobActivitiesTest`,
JATT = `JobAttributesTest`, JMT = `JobMetadataTest`, JTT = `JobTracingTest`, JPT = `JobPropertiesTest`.

## 4. Mapping per test class

### 4.1 `NjamsFacadeBaselineTest` (48 tests) - delete the class, 1 retained behaviour needs a new test

Plan: Task 6 deletes the class. Every row is either covered by a verified `NjamsFacetApiTest` mirror (the `_viaFacet`
suffix), disappears with the feature, or needs a new test (marked **NEW**).

| Test | Removed member | Facet replacement | Existing replacement test / NEW | Action | n |
|---|---|---|---|---|---|
| categoryIsUppercased | `getCategory` | `metadata().getCategory` | NFAT.categoryIsUppercased_viaFacet (same assertion) | DELETE | 1 |
| clientPathIsReturned | `getClientPath` | `metadata().getClientPath` | NFAT.clientPathIsReturned_viaFacet | DELETE | 1 |
| clientSessionIdAndCommunicationSessionIdAreTheSame | `getClientSessionId`, `getCommunicationSessionId` | `metadata().getClientSessionId` | Non-null part: NFAT.clientSessionIdMatchesBothLegacyGetters_viaFacet (first assertion, see 4.2). "Both legacy getters are equal" disappears with the two removed getters. | DELETE | 1 |
| clientVersionComesFromConstructorWhenNoVersionFile | `getClientVersion` | `metadata().getClientVersion` | NFAT.clientVersionComesFromConstructorWhenNoVersionFile_viaFacet | DELETE | 1 |
| sdkVersionIsNeverNull | `getSdkVersion` | `metadata()` | NFAT.sdkVersionIsNeverNull_viaFacet | DELETE | 1 |
| machineIsNeverNull | `getMachine` | `metadata()` | NFAT.machineIsNeverNull_viaFacet | DELETE | 1 |
| runtimeVersionIsSettable | `get/setRuntimeVersion` | `metadata()` | NFAT.runtimeVersionIsSettable_viaFacet | DELETE | 1 |
| runtimeVersionConstructorArgumentIsApplied | 5-arg constructor | `metadata().setRuntimeVersion` | Behaviour disappears with the 5-arg constructor; the setter path is covered by NFAT.runtimeVersionIsSettable_viaFacet | DELETE | 1 |
| addGlobalVariablesMergesIntoExisting | `addGlobalVariables`, `getGlobalVariables` | `model()` | NFAT.addGlobalVariablesMergesIntoExisting_viaFacet | DELETE | 1 |
| addGlobalVariablesAfterStartIsLenient | lenient legacy `addGlobalVariables` | throws after start | Leniency disappears; facet behaviour: NFAT.newAddGlobalVariablesThrowsAfterStart | DELETE | 1 |
| setRuntimeVersionAfterStartIsLenient | lenient legacy | throws after start | Disappears; NFAT.newSetRuntimeVersionThrowsAfterStart | DELETE | 1 |
| setGlobalVariablesPatternAfterStartIsLenient | lenient legacy | throws after start | Disappears; NFAT.newSetGlobalVariablesPatternThrowsAfterStart | DELETE | 1 |
| inherentFeaturesArePresentByDefault | `hasFeature` | `features().has` | NFAT.inherentFeaturesArePresentByDefault_viaFacet | DELETE | 1 |
| addFeatureIsIdempotent | `addFeature`, `getFeatures` | `features()` | NFAT.addFeatureIsIdempotent_viaFacet | DELETE | 1 |
| removeFeatureRemoves | `removeFeature` | `features()` | NFAT.removeFeatureRemoves_viaFacet | DELETE | 1 |
| removingInherentFeatureThrows | `removeFeature` | `features()` | NFAT.removingInherentFeatureThrows_viaFacet | DELETE | 1 |
| getFeaturesReturnsACopy | `getFeatures` | `features().list` | NFAT.getFeaturesReturnsACopy_viaFacet | DELETE | 1 |
| addFeatureAfterStartIsLenient | lenient legacy | throws after start | Disappears; NFAT.newFeatureAddThrowsAfterStart | DELETE | 1 |
| containerModeIsOnByDefaultAndSettableBeforeStart | `is/setContainerMode` | `features()` | NFAT.containerModeIsOnByDefaultAndSettableBeforeStart_viaFacet | DELETE | 1 |
| setContainerModeAfterStartThrows | `setContainerMode` | `features()` | NFAT.setContainerModeAfterStartThrows_viaFacet | DELETE | 1 |
| setReplayHandlerTogglesReplayFeature | `set/getReplayHandler` | `replay()` | NFAT.setReplayHandlerTogglesReplayFeature_viaFacet | DELETE | 1 |
| setReplayHandlerAfterStartIsLenient | lenient legacy | throws after start | Disappears; NFAT.newReplaySetHandlerThrowsAfterStart | DELETE | 1 |
| getProcessModelThrowsWhenAbsent | `getProcessModel(common.Path)` | `model().get` | NFAT.getProcessModelThrowsWhenAbsent_viaFacet | DELETE | 1 |
| createProcessRegistersModelUnderAbsolutePath | `createProcess`, `getProcessModel`, `getProcessModels` | `model()` | NFAT.createProcessRegistersModelUnderAbsolutePath_viaFacet | DELETE | 1 |
| getProcessModelsIsUnmodifiable | `getProcessModels` | `model().getAll` | NFAT.getProcessModelsIsUnmodifiable_viaFacet | DELETE | 1 |
| addProcessModelOfForeignInstanceThrows | `addProcessModel` | `model().add` | NFAT.addProcessModelOfForeignInstanceThrows_viaFacet | DELETE | 1 |
| addProcessModelIgnoresNull | `addProcessModel` | `model().add` | NFAT.addProcessModelIgnoresNull_viaFacet | DELETE | 1 |
| setTreeElementTypeForUnknownPathThrows | `setTreeElementType` | `model()` | NFAT.setTreeElementTypeForUnknownPathThrows_viaFacet | DELETE | 1 |
| setTreeElementTypeForClientPathWorks | `setTreeElementType` | `model()` | NFAT.setTreeElementTypeForClientPathWorks_viaFacet | DELETE | 1 |
| layouterAndDiagramFactoryAreReplaceable | `set/getProcessModelLayouter`, `set/getProcessDiagramFactory` | `model()` | NFAT.layouterAndDiagramFactoryAreReplaceable_viaFacet | DELETE | 1 |
| addImageAfterStartIsLenient | lenient legacy `addImage` | throws after start | Disappears; NFAT.newAddImageThrowsAfterStart | DELETE | 1 |
| sendAdditionalProcessBeforeStartThrows | `sendAdditionalProcess` | `model().additionalResources().build()` | NFAT.newAnnounceBeforeStartThrows | DELETE | 1 |
| sendAdditionalProcessSendsProjectMessageWithThatProcess | `sendAdditionalProcess` | additionalResources | NFAT.sendAdditionalProcessSendsProjectMessageWithThatProcess_viaFacet | DELETE | 1 |
| sendProjectMessageContainsAddedImage | `addImage`, `sendProjectMessage` | `model().send` | NFAT.sendProjectMessageContainsAddedImage_viaFacet | DELETE | 1 |
| jobLifecycleAfterStart | `getJobById`, `getJobs`, `removeJob` | `jobs()` | NFAT.newJobsApiMatchesLegacyBehavior covers get/getAll size/remove/get==null but not "`getAll()` is empty after remove" -> **NEW N7** | DELETE (after N7) | 1 |
| getJobsIsUnmodifiable | `getJobs` | `jobs().getAll` | NFAT.getJobsIsUnmodifiable_viaFacet | DELETE | 1 |
| instructionListenersAreAddableAndRemovable | `get/add/removeInstructionListener(s)` | `commands()` | NFAT.newCommandsApiWorks | DELETE | 1 |
| getInstructionListenersReturnsACopy | same | `commands().list` | NFAT.getInstructionListenersReturnsACopy_viaFacet | DELETE | 1 |
| pingInstructionIsAnswered | `onInstruction` | `commands().dispatch` (package-private) | No test exercises PING through `NjamsCommands.dispatch` (Task 3 step 1 plans a PING check; the params `clientId` and `category` asserted here are not part of it) -> **NEW N2** | DELETE (after N2) | 1 |
| getRequestHandlerInstructionReturnsClientId | `onInstruction` | `commands().dispatch` | **NEW N3** | DELETE (after N3) | 1 |
| unsupportedCommandIsRejected | `onInstruction` | `commands().dispatch` | **NEW N4** | DELETE (after N4) | 1 |
| logModeDefaultsToComplete | `getLogMode` | `configuration().getLogMode` | NFAT.newConfigurationApiWorks; also NjamsConfigurationTest.logModeDefaultsToComplete | DELETE | 1 |
| isExcludedIsFalseByDefaultAndTrueForNull | `isExcluded` | `configuration().isExcluded` | NFAT.isExcludedIsFalseByDefaultAndTrueForNull_viaFacet; NjamsConfigurationTest.isExcludedWithNullPathIsTreatedAsExcluded | DELETE | 1 |
| configurationIsNeverNull | `getConfiguration` | `configuration().get` | NFAT.newConfigurationApiWorks (assertNotNull on `get()`); NjamsConfigurationTest.getReturnsConfiguration | DELETE | 1 |
| argosCollectorAddAndRemoveDoNotThrow | `add/removeArgosCollector` | `argos()` | NFAT.argosCollectorAddAndRemoveDoNotThrow_viaFacet | DELETE | 1 |
| removeSerializerReturnsTheRegisteredOneAndRestoresDefault | `add/remove/serialize` | `serializers()` | NFAT.newSerializersApiWorks only asserts `remove` is non-null; "returns the registered one, restores the default, second remove and null key return null" is not covered -> **NEW N5** | DELETE (after N5) | 1 |
| equalsAndHashCodeAreBasedOnClientPath | none removed (uses only constructors/equals) | - | Kept behaviour; the class is deleted, so it needs a home. Task 6 step 1 already plans it in `NjamsTest` -> **NEW N1** (move the body and add the Mockito-mock inequality case) | DELETE (after N1) | 1 |
| getSenderReturnsNonNullAndIsCached | `getSender` | package-private `Njams.sender()` | **NEW N6** (`NjamsTest`, package `sdk`) | DELETE (after N6) | 1 |

### 4.2 `NjamsFacetApiTest` (76 tests)

| Test | Removed member | Facet replacement | Replacement / NEW | Action | n |
|---|---|---|---|---|---|
| deprecatedMetadataMutatorsStayLenientAfterStart | lenient `setRuntimeVersion`/`addGlobalVariables`/getters | - | Leniency disappears; guards are pinned by newAddGlobalVariablesThrowsAfterStart, newSetRuntimeVersionThrowsAfterStart | DELETE | 1 |
| clientSessionIdMatchesBothLegacyGetters_viaFacet | lines 158-159 use `getClientSessionId`, `getCommunicationSessionId` | - | Keep line 157 (`assertNotNull(metadata().getClientSessionId())`), drop the two assertions on the removed getters; rename optional | REDUCE | 1 |
| deprecatedFeatureAddStaysLenientAfterStart | lenient `addFeature` | - | Disappears; newFeatureAddThrowsAfterStart | DELETE | 1 |
| replayInstructionIsAnswered_viaFacet | `onInstruction` (act line 310) | `commands().dispatch(inst)` (same package) | assertions untouched | RETARGET-SETUP | 1 |
| layouterAndDiagramFactoryAreReplaceable_viaFacet, other tests using `SimpleProcessModelLayouter` | keep mark | - | - | KEEP-UNCHANGED | - |
| all other tests | none | - | - | KEEP-UNCHANGED | 69 |

### 4.3 `NjamsTest` (33 tests, base class `instance = new Njams(Path.of(), ...)`)

| Test | Removed member | Facet replacement | Replacement / NEW | Action | n |
|---|---|---|---|---|---|
| testSerializer | `addSerializer`, `serialize` | `serializers()` | NFAT.serializerHierarchyResolution_viaFacet (same three assertions) | DELETE | 1 |
| serializeWithSizeLimitForwardsLimitToRegisteredSerializer | `addSerializer`, `serialize(t,int)` | `serializers()` | NFAT.serializeWithSizeLimitForwardsLimitToRegisteredSerializer_viaFacet | DELETE | 1 |
| serializeWithoutSizeLimitStillUsesMaxValue | `addSerializer`, `serialize` | `serializers()` | NFAT.serializeWithoutSizeLimitStillUsesMaxValue_viaFacet | DELETE | 1 |
| stopReceiverAfterStartupFailureDeregisters... (line 255 `instance.getSender()`) | `getSender` | package-private `instance.sender()` (same package) | assertions untouched (`recoveryListenerCount` etc.) | RETARGET-SETUP | 1 |
| testOnCorrectSendProjectMessageInstruction, testOnNoReplyHandlerFoundReplayMessageInstruction | `onInstruction` | `commands().dispatch(inst)` | assertions on `inst.getResponse()` untouched | RETARGET-SETUP | 2 |
| testOnCorrectReplayMessageInstruction, testOnThrownExceptionReplayMessageInstruction | `setReplayHandler`, `onInstruction` | `replay().setHandler`, `commands().dispatch` | assertions untouched | RETARGET-SETUP | 2 |
| testHasNoProcessModel, testHasProcessModel | `hasProcessModel(common.Path)`, `createProcess` | `model().has/create` | NjamsModelTest.hasReflectsRegisteredModels + createAndGetRoundtrip; NFAT.hasByAbsolutePath_isFalseWhenAbsent | DELETE | 2 |
| testNoProcessModelForNullPath | `hasProcessModel(null)` | `model().has((Path) null)` | `NjamsModel.has(Path)` returns false for null but no test pins it -> **NEW N8** | DELETE (after N8) | 1 |
| disableDataMaskingDisablesAllDataMasking | `getConfiguration().setDataMasking` (line 491) | `configuration().get().setDataMasking` (keep mark) | assertion untouched | RETARGET-SETUP | 1 |
| defaultLayouter_isCommonBfsModelLayouter | `getProcessModelLayouter` | `model().getLayouter` | NFAT.defaultLayouter_isCommonBfsModelLayouter_viaFacet (same assertion) | DELETE | 1 |
| sendProjectMessage_propagatesGlobalVariables, sendProjectMessage_propagatesGlobalVariablesPattern | `addGlobalVariables`, `setGlobalVariablesPattern`, `sendProjectMessage` (act lines) | `model().addGlobalVariables/setGlobalVariablesPattern/send` | assertions on `ProjectMessage` untouched; no facet test asserts that the sent message carries the pattern, so the test stays | RETARGET-SETUP | 2 |
| setGlobalVariablesPattern_acceptsValidPatternAndIsReturnedByGetter, _acceptsPatternWithOptionalDefaultGroup, _nullClearsThePattern, _rejectsInvalidRegex, _rejectsMissingNameGroup, _rejectsMissingFullGroup | `set/getGlobalVariablesPattern` | `model()` | exact mirrors NFAT.setGlobalVariablesPattern_*_viaFacet (6 tests, same patterns and assertions); also GlobalVariablesTest | DELETE | 6 |
| setDataMaskingViaSettings, disableDataMaskingViaSettings, disableDataMaskingDisablesAllDataMasking (setup), enableDataMaskingWithoutRegex, defaultLayouter... (setup), testStartReturnsFalseWhenSenderConstructionFails | `Settings` | `ClientSettings.from(...)` | - | MIGRATE-SETUP | (class counted in 5.3) |
| `import ...common.Path` users at lines 435-446 | covered by the testHas* rows above | - | - | - | - |
| all other tests | none | - | - | KEEP-UNCHANGED | 14 |

### 4.4 `NjamsJobsTest` (6 tests) - NEEDS EXPLICIT PERMISSION (assertion lines)

`NjamsJobs` will call `ExceptionSupport.suppressException(() -> job.tracing().setDeepTrace(true))`. The test's `Job` is a Mockito mock whose `tracing()` returns `null` today; the call would throw inside `suppressException`, be swallowed and the `verify` would fail.

| Test | Removed member | Facet replacement | Replacement / NEW | Action | n |
|---|---|---|---|---|---|
| setReplayMarkerForPresentJobSetsDeepTrace (line 88), rememberedReplayMarkerIsAppliedWhenJobIsAddedLater (line 101) | `Job.setDeepTrace(true)` verified on the mock | `job.tracing().setDeepTrace(true)` | Stub `when(job.tracing()).thenReturn(tracing)` in helper `job(...)` with a `Mockito.mock(JobTracing.class)` (public, non-final); change `verify(job).setDeepTrace(true)` to `verify(tracing).setDeepTrace(true)`. **Assertion change** (verification target), same behaviour. | RETARGET-OBSERVER (permission) | 2 |
| the other four tests | none | - | - | KEEP-UNCHANGED | 4 |

### 4.5 `FailedStartupCleanupTest` (2 tests) - NEEDS EXPLICIT PERMISSION (one assertion line)

`retriedStartAfterFailureHasNoDuplicateListeners`, line 58:
`assertEquals(1, listeners.stream().filter(l -> l == njams).count())` identifies the registered `Njams` itself, which stops being a listener (Task 3 registers a private dispatcher). Proposed replacement:
`assertEquals(1, listeners.stream().filter(l -> !(l instanceof ConfigurationInstructionListener)).count())`
(one listener that is not the configuration listener = the dispatcher; the other assertion on line 59 for `ConfigurationInstructionListener` and the empty-list assertion on line 52 stay). The behaviour pinned is unchanged ("no duplicate listener after a failed and retried start"). The dispatcher-answers-PING part is added by the Task 3 step 1 test, not here. `failedStartRemovesArgosCollectors`: KEEP-UNCHANGED. Action: RETARGET-OBSERVER (permission), n = 1.

### 4.6 `NjamsSampleTest` (8 tests) - NEEDS EXPLICIT PERMISSION (28 assertion lines)

All 8 tests are kept; none deleted. Changes:

| Lines | Removed member | Replacement | Kind | n (lines) |
|---|---|---|---|---|
| `addImage` (21 lines), `setTreeElementType` (8), `getConfiguration()` (1, line 143), `Job.createActivity` (9), `Job.end()` (9) | `Njams`/`Job` legacy | `model().addImage/setTreeElementType`, `configuration().get()`, `job.activities().create`, `job.end(true)` | RETARGET-SETUP (no assertion) | 48 |
| `((JobImpl) job).flush()` (26 lines, tests `testGroupInGroupWithFlushes`, `testGroupInGroupWithFlushesAndEncoded`) | `flush()` narrowed to package-private | test helper `JobFlushAccess` in package `logmessage` (plan Task 5) | RETARGET-SETUP | 26 |
| `assertThat(job.getAttribute("json"/"xml"), ...)` lines 157, 158 | `Job.getAttribute` | `job.attributes().get(...)` | **assertion text, accessor only** (permission) | 2 |
| `assertThat(job.getActivities().size(), is(N))` 26 lines (740-833 and 996-1089) | `Job.getActivities` | `job.activities().getAll().size()` | **assertion text, accessor only** (permission); expected values N unchanged | 26 |
| `Settings` (12 hits) | settings layer | `ClientSettings` | MIGRATE-SETUP | - |

No existing facet test replaces these end-to-end scenarios, so no deletion.

### 4.7 `PathTest` (sdk, 131 tests) and `common/PathTest` (10 tests)

| Test | Removed member | Replacement | Action | n |
|---|---|---|---|---|
| PathTest.ofFromNullLegacyReturnsRoot, ofFromLegacyConvertsToNewPath, ofFromLegacySinglePathString, toLegacyPathFromRoot, toLegacyPathPreservesPathString, toLegacyPathRoundTripsViaGet | `Path.of(common.Path)`, `toLegacyPath()` | Behaviour disappears with the legacy class; `of(String...)`/`resolve` are covered by the other PathTest tests | DELETE | 6 |
| common/PathTest.* (testGetObjectName, testAddBase, testAdd, testAddPath, testAddPostfix, testGetParent, testHashCode, testEquals, testCompareTo, testGetAllPaths) | class `common.Path` | Class is deleted; equivalent behaviours of the new type: PathTest.parentAtDepthTwo, equalsIs*, hashCodeIsStable, equalInstancesHaveSameHashCode, nameAtDepthTwo, segmentsOf*. Methods with no new-`Path` counterpart (`add*`, `compareTo`, `getAllPaths`) are legacy-class API and disappear with it. | DELETE (file) | 10 |
| PathTest other 125 | none | - | KEEP-UNCHANGED | 125 |

### 4.8 `ConfigurationPathOverloadsTest` (18 tests, `@SuppressWarnings("removal")`)

| Test | Removed member | New-Path counterpart in same file | Action | n |
|---|---|---|---|---|
| legacyGetProcessCreatesAndReturnsSameConfigurationAsStringVariant | `getProcess(common.Path)` | getProcessWithNewPathSharesConfigurationWithStringAndLegacyVariants (first three assertions are the new-Path case) | DELETE | 1 |
| legacyHasProcessReflectsExistenceOfProcessConfiguration | `hasProcess(common.Path)` | hasProcessWithNewPathReflectsExistenceOfProcessConfiguration (identical) | DELETE | 1 |
| legacyHasProcessExcludeFilterFollowsSetProcessExcluded | legacy overloads | newAndLegacyExcludeSettingsAreInterchangeable (new-Path half) | DELETE | 1 |
| legacyIsProcessExcludedWithNullPathIsExcluded | `isProcessExcluded(common.Path)` | isProcessExcludedWithNullNewPathIsExcluded | DELETE | 1 |
| legacyIsSelectedWithNullPathIsFalse | `isSelected(common.Path)` | isSelectedWithNullNewPathIsFalse | DELETE | 1 |
| legacySetExcludedOnFilterAddsAndRemovesExcludeFilter | `setExcluded(common.Path)` | setExcludedOnFilterWithNewPathAddsAndRemovesExcludeFilter (new-Path half) | DELETE | 1 |
| legacyIsSelectedIgnoresInstanceIdentity | `isSelected(common.Path)` | Instance identity of legacy objects only; with the new `Path` the instance is canonical (PathTest.sameSegmentsReturnSameInstance) | DELETE | 1 |
| getProcessWithNewPathSharesConfigurationWithStringAndLegacyVariants | line `assertSame(created, config.getProcess(legacy("a","b")))` | the other three assertions of the test | REDUCE | 1 |
| newAndLegacyExcludeSettingsAreInterchangeable | `hasProcessExcludeFilter(legacy)`, `isProcessExcluded(legacy)`, `setProcessExcluded(legacy,false)` | the Path-only assertions that remain: after reduction the second half becomes `setProcessExcluded(Path.of("a","b"), false)` is needed to keep the "false" assertions; the unset call is an act line (RETARGET-SETUP), the legacy assertions are dropped | REDUCE | 1 |
| setExcludedOnFilterWithNewPathAddsAndRemovesExcludeFilter | line `assertTrue(filter.hasExcludeFilter(legacy("a","b")))` | other assertions | REDUCE | 1 |
| isSelectedGivesSameResultForNewAndLegacyPath | `isSelected(legacy)` | the new-Path assertions | REDUCE | 1 |
| isSelectedWithLegacyPathFirstThenNewPathAgree | `setProcessExcluded(legacy)`, `isSelected(legacy)` | Only one form remains -> the test collapses to the new-Path case already in setExcludedOnFilterWithNewPathUpdatesSelection / isSelectedGivesSameResultForNewAndLegacyPath (reduced) | DELETE | 1 |
| setExcludedOnFilterWithLegacyPathUpdatesSelection | `setExcluded(legacy)`, `isSelected(legacy)` | setExcludedOnFilterWithNewPathUpdatesSelection (identical with new Path) | DELETE | 1 |
| hasProcessWithNewPathReflectsExistence..., isProcessExcludedWithNullNewPath..., isSelectedWithNullNewPath..., setExcludedOnFilterWithNewPathUpdatesSelection, removingExplicitExcludeDoesNotSelect... | none | - | KEEP-UNCHANGED | 5 |

(Row counts: 9 DELETE, 4 REDUCE, 5 KEEP = 18.) The class-level `@SuppressWarnings("removal")` becomes unnecessary and the `legacy(...)` helper is removed.

### 4.9 `ProcessFilterTest` (19 tests) - input conversion at scale

All 19 tests build their inputs with `new com.im.njams.sdk.common.Path(">a>b>c>")` and call the legacy overloads
`ProcessFilter.isSelected(common.Path)` / `hasExcludeFilter(common.Path)` / `Configuration.isProcessExcluded(common.Path)` / `setProcessExcluded(common.Path, boolean)` / `hasProcessExcludeFilter(common.Path)` (75 assertion lines, 11 act lines). The new-`Path` overloads have identical semantics (SDK-205) but are tested only by the narrow `ConfigurationPathOverloadsTest`, so the whole decision matrix (include/exclude, patterns, settings patterns, old config, concurrency) is currently pinned through the legacy overloads only.

Proposed: RETARGET-ARG for all 19: `new Path(str)` (legacy) becomes `Path.resolve(str)` (new type, same `>a>b>` string form); expected booleans untouched. Precondition to verify when doing it: `Path.resolve(">a>.>c>").toString()` equals the legacy string for each literal used (segments `.` and empty-segment handling differ in principle: `resolve` drops empty segments). The `Builder.process(String, boolean)` helper line 96 becomes `config.setProcessExcluded(Path.resolve(path), exclude)`. The matrix is pure behaviour of kept code, so no row is deleted. n = 19.
Because the argument type is part of every `assertTrue/False(filter.isSelected(...))` line, this is counted as an assertion-text change in section 6 (class "argument only").

### 4.10 `JobFacadeBaselineTest` (41 tests) - class stays, reduced (kept lifecycle/status/flush tests remain)

| Test | Removed member | Facet replacement | Replacement / NEW | Action | n |
|---|---|---|---|---|---|
| deprecatedEndDelegatesToEndTrue | `Job.end()` | `end(true)` | Behaviour disappears with `end()`; `end(true)` -> SUCCESS is pinned by endTrueWithoutStatusYieldsSuccess | DELETE | 1 |
| addActivityBeforeStartThrows | strict legacy `createActivity().build()` before start | `activities().create` allows pre-start | Behaviour inverted by design; JFAT.newActivitiesAddWorksBeforeStart + prestartActivityIsFlushedAfterStart | DELETE | 1 |
| activityLifecycleAfterStart | `getActivityByInstanceId/ByModelId/getRunning.../getCompleted.../getActivities` | `activities()` | JFAT.activityLifecycleAfterStart_viaFacet (see 4.11 for the reduced running/completed lines) | DELETE | 1 |
| getActivitiesReturnsDetachedCopy | `getActivities` | `activities().getAll` | JFAT.getActivitiesReturnsDetachedCopy_viaFacet | DELETE | 1 |
| secondStartActivityThrows | `createActivity` | `activities().create` | JFAT.secondStartActivityThrows_viaFacet | DELETE | 1 |
| startActivityIsTracked | `getStartActivity` | `activities().getStart` | JFAT.startActivityIsTracked_viaFacet | DELETE | 1 |
| createSubProcessReturnsBuilder | `createSubProcess`, `getActivityByModelId` | `activities()` | JFAT.createGroupAndSubProcess_viaFacet (builder part); `getByModelId` after create pinned by JAT.getByModelIdReturnsLastAdded | DELETE | 1 |
| activityErrorEventIsCommittedOnFailedEnd, activityErrorEventIsDiscardedOnSuccessfulEnd | `createActivity` (arrange); `setActivityErrorEvent` is only narrowed | `activities().create` | assertions untouched | RETARGET-SETUP | 2 |
| attributesArePutAndQueried | `addAttribute`, `getAttribute`, `hasAttribute`, `getAttributes` | `attributes()` | JFAT.attributesArePutAndQueried_viaFacet | DELETE | 1 |
| nullAttributeValueIsIgnored | `addAttribute`, `hasAttribute` | `attributes()` | JFAT.nullAttributeValueIsIgnored_viaFacet | DELETE | 1 |
| recordingAddsNjamsRecordedAttribute | `getAttribute("$njams_recorded")` in `assertEquals` (line 274) | `attributes().get(...)` | No other test observes `$njams_recorded` via the job (StartDataLimitTest reads it via `getAttributes`, see 4.18). `isRecording()` (line 275) only narrowed | RETARGET-OBSERVER (*) | 1 |
| correlationLogIdDefaultsToLogIdAndIsSettable | `get/setCorrelationLogId` | `metadata()` | JFAT.correlationLogIdDefaultsToLogIdAndIsSettable_viaFacet | DELETE | 1 |
| parentAndExternalLogIdAreSettable | same | `metadata()` | JFAT.parentAndExternalLogIdAreSettable_viaFacet | DELETE | 1 |
| businessServiceAndObjectAcceptStringAndPath | `set/getBusiness*` | `metadata()` | JFAT.businessServiceAndObjectAcceptStringAndPath_viaFacet | DELETE | 1 |
| businessStartAndEndAreSettable | same | `metadata()` | JFAT.businessStartAndEndAreSettable_viaFacet | DELETE | 1 |
| overlongFieldValueIsTruncated | `setParentLogId`, `getParentLogId` | `metadata()` | JFAT.overlongFieldValueIsTruncated_viaFacet | DELETE | 1 |
| limitLengthTruncatesToMaxMinusOne | `limitLength` (narrowed) | - | same package | KEEP-UNCHANGED | 1 |
| propertiesRoundTrip | `get/set/has/removeProperty` | `properties()` | JFAT.propertiesRoundTrip_viaFacet | DELETE | 1 |
| deepTraceAndTracesFlags | `is/setDeepTrace`, `isTraces`, `setTraces` | `tracing()` | JFAT.deepTraceFlag_viaFacet (deep trace and `isTraces == false`) + JTT.deepTraceCanBeToggled, JTT.tracesCanBeToggled (the package-private `JobTracing.setTraces` replaces the removed `JobImpl.setTraces`) | DELETE | 1 |
| needsDataIsTrueForDeepTraceAndStarterModels | `setDeepTrace` (act line 367) | `tracing().setDeepTrace` | assertions untouched | RETARGET-SETUP | 1 |
| timerFlushBeforeStartIsSkippedSilently | `timerFlush` (narrowed) | - | Task 5 keeps behaviour | KEEP-UNCHANGED | 1 |
| estimatedSizeGrowsWithContent | `addAttribute` (act line 384) | `attributes().add`; `getEstimatedSize`/`addToEstimatedSize` only narrowed | assertions untouched | RETARGET-SETUP | 1 |
| lastFlushIsInitialized | `getLastFlush` | none (no callers) | Behaviour disappears with the deleted member | DELETE | 1 |
| getNjamsReturnsOwner | `getNjams` (narrowed) | - | same package | KEEP-UNCHANGED | 1 |
| newJobIsCreatedNotStartedNotFinished, startSetsRunningAndStartTime, explicitStartTimeSurvivesStart, setStartTimeNullIsIgnoredWithWarning, setStatusBeforeStartOnlyWarns, setStatusNullOrCreatedIsIgnored, maxSeverityEscalatesButNeverDecreases, endTrueWithoutStatusYieldsSuccess, endFalseYieldsError, endTwiceThrows, endRemovesJobFromRegistry, addPluginDataItemIsAccepted, noPayloadLimitConfiguredMeansPassThrough, toStringContainsLogAndJobId, flushOnNeverStartedJobSendsNothing, endOnNeverStartedJobLogsErrorAndSendsNothing | none (`setStatus` kept, `flush` narrowed) | - | - | KEEP-UNCHANGED | 16 |

(Counts: 1+1+1+1+1+1+1 = 7 DELETE for the activity block, plus 8 attribute/metadata/properties/trace DELETEs... see summary table for the final numbers.) After the reduction the class keeps 24 tests (16 unchanged + 2 + 1 needsData + 1 estimated + limitLength + timerFlush + getNjams + recording); 17 are deleted.

### 4.11 `JobFacetApiTest` (29 tests)

| Test | Removed member | Replacement | Action | n |
|---|---|---|---|---|
| deprecatedMetadataSettersStayLenientAfterEnd | lenient `setCorrelationLogId/ParentLogId` after end | Disappears; newSetCorrelationLogIdThrowsAfterEnd, newSetParentLogIdThrowsAfterEnd | DELETE | 1 |
| deprecatedAddAttributeStaysLenientAfterEnd | lenient `addAttribute` after end | Disappears; newAttributesAddThrowsAfterEnd | DELETE | 1 |
| activityLifecycleAfterStart_viaFacet | `JobActivities.getRunningByModelId/getCompletedByModelId` (lines 267, 268, 270, 271) | - | REDUCE: drop these four assertions, keep the other four (instance id, model id, `activity.end()`, `getAll().size() == 1`) | REDUCE | 1 |
| all other tests | none | - | KEEP-UNCHANGED | 26 |

### 4.12 `JobActivitiesTest` (15 tests)

`getRunningByModelId`/`getCompletedByModelId` (package-private, forRemoval) are removed with `findLastByModelId`'s status filter; `getByModelId` stays.

| Test | Action | Notes | n |
|---|---|---|---|
| getRunningAndCompletedByModelId | DELETE | Only removed members; `getByModelId` last-added is pinned by getByModelIdReturnsLastAdded | 1 |
| statusLookupsWalkBackPastNonMatchingCandidates | DELETE | Only the last assertion (`getByModelId` == third) concerns a kept member; it duplicates getByModelIdReturnsLastAdded | 1 |
| getByModelIdFallsBackToRemainingAfterRemoveNotRunning | REDUCE | drop lines 135-136; keep 127, 134 | 1 |
| getByModelIdReturnsNullWhenAllMatchesEvicted | REDUCE | drop lines 147-148; keep 146 | 1 |
| lookupIsUnaffectedByOtherModelIds | REDUCE | drop lines 195, 197, 198; keep 194, 196 | 1 |
| groupLoopResolvesToTheMostRecentIteration | REDUCE | drop lines 219-220; keep 217-218 | 1 |
| statusLookupsIgnoreAnActivityThatWasAddedButNeverStarted | REDUCE | drop lines 236-237; keep 234 (unstarted activity returned by `getByModelId`); test name no longer fits, rename optional | 1 |
| other 8 tests | KEEP-UNCHANGED | | 8 |

### 4.13 `JobImplTest` (36 tests, extends `AbstractTest`)

| Test | Removed member | Replacement / NEW | Action | n |
|---|---|---|---|---|
| testFlushGroupWithChildren | `createGroup` (97), `getActivities` (106, 124 assigned to a variable, assertions on the variable), `flush` narrowed | `activities().createGroup`, `activities().getAll()` | RETARGET-SETUP | 1 |
| testDataMaskingAfterFlushing | `common.Path`, `Njams.createProcess/getProcessModel(common.Path)`, `getSender()` stub on a spy (line 171), `end()` | `model().create/get(Path)`, capture via `TestSender.setSenderMock(...)` instead of stubbing `getSender()` (the flusher will read the sender from the task registry, a spy cannot inject it), `end(true)`; `checkAllFields()` assertions untouched | RETARGET-SETUP (larger rewrite of arrange part) | 1 |
| fillJob helper (lines 194-200) | `set*LogId`, `setBusiness*`, `addAttribute` | `metadata()`, `attributes().add` | RETARGET-SETUP | helper |
| testIsFinished (333), testJobEndWithoutStart (429) | `end()` | `end(true)` (same semantics: `end()` delegated to `end(true)`, proven by JobFacadeBaselineTest.deprecatedEndDelegatesToEndTrue) | RETARGET-SETUP | 2 |
| testJobFlushWithoutStart | `flush` narrowed | - | KEEP-UNCHANGED | 1 |
| testAddActivityWithoutStart | strict legacy `addActivity` | Behaviour inverted by design; JFAT.newActivitiesAddWorksBeforeStart | DELETE | 1 |
| testAddAttributeWithStart | `addAttribute`, `getAttribute` | JATT.addStoresValueAndReportsPresence + JFAT.attributesArePutAndQueried_viaFacet | DELETE | 1 |
| testAddAttributeWithoutStart, testAddAttributeFlushAndGetAttribute | `addAttribute`, `getAttribute(s)` on an unstarted job / after flush | JATT.flushIntoMovesPendingToFlushed (unit level) does not cover job-level "add before start, flush without start, still readable" -> **NEW N9** | DELETE (after N9) | 2 |
| testSetStartActivity, testSetStartActivityAndFlushIt, setMoreStartActivitiesAfterFlushingTheFirstStartActivity | `getStartActivity` in assertions (485, 491, 507, 530); `hasOrHadStartActivity` is a field | JAT.startActivityIsTrackedAndDuplicatesRejected covers set/duplicate but not "start activity becomes null after flush while `hasOrHadStartActivity` stays and a new start still throws" | RETARGET-OBSERVER (*) (alternative: **NEW N10**, copy in facet form, then delete) | 3 |
| setMoreStartActivities | none (helper `getStartedActivityForJob` retargeted) | - | RETARGET-SETUP | 1 |
| testGetActivityByInstanceIdReturnsActivity, ...ReturnsNullForUnknownId | `getActivityByInstanceId` | JAT.getByInstanceIdReturnsActivityOrNull (both assertions) | DELETE | 2 |
| testGetActivitiesReturnsAllAddedActivities | `getActivities`, `createActivity` | JAT.getAllReturnsDetachedCopy + JAT.reAddingAnAlreadyRegisteredActivityKeepsItsOriginalPosition (`getAll().size()==2`) | DELETE | 1 |
| freshJobHasBaseEstimatedSize, subProcessActivityAddsConstantToEstimatedSize | `getEstimatedSize` (narrowed) | - | KEEP-UNCHANGED | 2 |
| eventPayloadIncreasesEstimatedSize, addingActivitiesGrowsRunningEstimateByBase, eventMessageIncreasesEstimatedSize, eventCodeIncreasesEstimatedSize, stackTraceIncreasesEstimatedSize, startDataIncreasesEstimatedSize, timerFlushDoesNotFlushBeforeSizeOrIntervalReached, timerFlushTriggersOnSizeFromActivitiesAlone, timerFlushSendsCompletedActivityOfOtherwiseIdleRunningJob | `createActivity` (arrange); `getEstimatedSize`/`timerFlush`/`flush` narrowed | `activities().create` | RETARGET-SETUP | 9 |
| attributeIncreasesEstimatedSize | `addAttribute` (act) | `attributes().add`; assertion on narrowed `getEstimatedSize` untouched | RETARGET-SETUP | 1 |
| testSetStartTimeBeforeStart, testDoesntSetStartTimeBeforeStart, testSetStartTimeAfterStart, testSetStartTimeAfterStartWithSettingBackToCreated, getSerializeSizeHint* (3), testHiddenAttributeName | none (`setStatus` kept) | - | KEEP-UNCHANGED | 8 |
| lines 159, 165 (`common.Path`) | covered by testDataMaskingAfterFlushing row | - | - | - |

### 4.14 `AbstractTest` and the other `logmessage` tests (shared helpers)

| Class / member | Removed member | Facet replacement | Action |
|---|---|---|---|
| `AbstractTest.createDefaultActivity/getStartedActivityForJob/createFullyFilledActivity` (lines 116, 139, 164) | `Job/JobImpl.createActivity` | `job.activities().create(model)` | RETARGET-SETUP (helper; affects every subclass test indirectly, no assertion) |
| `AbstractTest` line 78 | `Settings` | `ClientSettings` | MIGRATE-SETUP |
| ActivityBuilderTest (8 tests): buildGeneratesInstanceIdAndStartsActivity (`getActivityByInstanceId` in assertSame, line 46), explicitInstanceIdIsHonored, fluentSettersAreAppliedToBuiltActivity, setStarterMarksStartActivity (`getStartActivity` in assertSame, line 95), stepFromNullTransitionModelThrows | `createActivity` (act), `getActivityByInstanceId`, `getStartActivity` | `activities().create/getByInstanceId/getStart`; covered by JAT.getByInstanceIdReturnsActivityOrNull and JAT.startActivityIsTrackedAndDuplicatesRejected for the lookup, but the builder tests assert the builder result | RETARGET-SETUP for `createActivity` (5 tests); RETARGET-OBSERVER (*) for lines 46, 95 (2 tests, 2 assertion lines) |
| ActivityFlagTruncationTest (prepare: `setDeepTrace`; 2 tests: `Njams.addSerializer`) | `JobImpl.setDeepTrace`, `Njams.addSerializer` | `tracing().setDeepTrace`, `serializers().add` | RETARGET-SETUP (3 places) |
| ActivityImplExtractDataTest (3 tests: `getConfiguration`, `setDeepTrace`, `addSerializer`) | same | `configuration().get()`, `tracing()`, `serializers()` | RETARGET-SETUP (8 places) |
| ActivityImplTest.testOverrideJobAttributesWithActivityAttributes (6 assertion lines 161-171: `job.getAttribute(key)`) | `JobImpl.getAttribute` | `job.attributes().get(key)` | RETARGET-OBSERVER (*) (1 test, 6 assertion lines). No facet test covers "activity attribute overwrites job attribute (last write wins)" |
| DataMaskingTest.mockFields (stubs `JOB.isDeepTrace()` returning true, `getNjams` narrowed, `NJAMS.serialize`) | `JobImpl.isDeepTrace`, `Njams.serialize` | The mock job already returns a real `JobTracing` (`TRACING`); replace the `isDeepTrace()` stub by `TRACING.setDeepTrace(true)`; `getNjams` stub unchanged | RETARGET-SETUP (stub), 1 place |
| DataMaskingTest.addPatternsFromProperties | `DataMasking.addPatterns(Properties)` | `addPatterns(ClientSettings)` | DELETE: DataMaskingTest.addPatternsFromSettings (same prefix filtering with an irrelevant key, one pattern) |
| DataMaskingTest.addPatternsFromSettings, others | `Settings` | `ClientSettings` | MIGRATE-SETUP |
| ExtractHandlerTest (configureNjams, testExtract) | `Njams.createProcess/getProcessModel(common.Path)`, `getConfiguration()` stubbed with `doReturn(conf).when(njams).getConfiguration()` on a `spy(Njams)`, `createActivity`, `Settings`, `common.Path` | `model().create/get(Path)`; **the spy stub needs a rethink**: main code will read `njams.configuration().get()`; stub `njams.configuration()` with a facet mock/spy returning `conf` and make sure `isExcluded(...)` used by `JobRuntimeConfig` also resolves against `conf` | RETARGET-SETUP (risk: behaviour of facet spy) |
| GroupImplTest.testRemoveChildActivities (`Job.createGroup`) | `createGroup` | `activities().createGroup` | RETARGET-SETUP (1) |
| JobConcurrencyTest, JobAttributesTest.addContributesToEstimatedSize, SubProcessActivityImplTest | `getEstimatedSize`, `addToEstimatedSize` (narrowed) | - | KEEP-UNCHANGED |
| JobErrorHandlingTest (newBuiltActivity: `createActivity`; commitReaddsActivityThatWasAlreadySent lines 107, 113: `getActivityByInstanceId` in assertNull / assertSame) | `createActivity`, `getActivityByInstanceId` | `activities().create/getByInstanceId` | RETARGET-SETUP (helper) + RETARGET-OBSERVER (*) (1 test, 2 assertion lines); `setActivityErrorEvent` is only narrowed (same package) |
| JobInstrumentedTest | `JobImpl.setInstrumented()` (lines 79, 113; `@SuppressWarnings("removal")`) | `tracing().setInstrumented()` | exclusiveModeSendsJobThatIsInstrumented: DELETE (equivalent exclusiveModeSendsJobInstrumentedViaFacet); facetFlagIsSharedWithDeprecatedJobImplMethod: DELETE (its facet half is JTT.instrumentedIsLatchedOnce; the "shared with deprecated method" half disappears). The class-level `@SuppressWarnings("removal")` can then go. n = 2 |
| JobRuntimeConfigTest.nullConfigurationFallsBackToDefaults | Mockito `when(njamsMock.getConfiguration()).thenReturn(null)` (line 60) | stub `njamsMock.configuration()` returning a mock facet whose `get()` returns null | RETARGET-SETUP (1). Other tests in the class use `isActiveTracepoint`/`getActivityConfiguration` on `JobRuntimeConfig`, not on `JobImpl`: unchanged |
| JobTest.testEventExtract | `getConfiguration()` (80, 82), `createActivity` (96), `Settings` | facets | RETARGET-SETUP + MIGRATE-SETUP |
| JobTruncationTest.activity, StartDataOncePerJobTest.newActivity | `createActivity` (helpers) | `activities().create` | RETARGET-SETUP (2 helpers) |
| LimitPayloadTest (init: `setDeepTrace`; testFields lines 137, 139: `getAttributes()` in assertions) | `JobImpl.setDeepTrace`, `Job.getAttributes` | `tracing()`, `attributes().getAll()` | RETARGET-SETUP (1) + RETARGET-OBSERVER (*) (1 test, 2 lines) |
| StartDataLimitTest (5 tests, `activity.getJob().getAttributes().get(RECORDED)` lines 36-76) | `Job.getAttributes` | `attributes().get(RECORDED)` | RETARGET-OBSERVER (*) (5 tests, 5 lines) |

### 4.15 `client` and `communication` tests

| Class | Test(s) | Removed member | Replacement | Action | n |
|---|---|---|---|---|---|
| CleanTracepointsTaskTest | whole class (constructor line 76, `@Before testStopAll` line 83: `njamsMock.getClientPath()` stubs, tests 87/101/156, fillProcessConfiguration 245/211/217, checkTraceMessage 252/254) | `Njams.getClientPath/getConfiguration/getSdkVersion`; plan also changes `start(njams)` to `start(njams, sender)` | `metadata()`, `configuration().get()`; the `mock(Njams.class)` instances must stub a facet mock: `njamsMock.metadata()` returning a `NjamsMetadata` mock whose `getClientPath()` is `Path.of("A")` or `null` (tests testStartWithNullClientPathNjams, testStopWithNullClientPath, testStartNormalWithMultipleNjams) | RETARGET-SETUP for 9 places; RETARGET-OBSERVER (*) for lines 211, 217, 252, 254 (assertions with `njams.getConfiguration()` / `getSdkVersion()` / `getClientPath()`, 3 tests: testRun, the assertion helper `checkTraceMessage`) | 13 (class) |
| TraceMessageBuilderTest | testBuild... (`checkTraceMessage`: `njams.getSdkVersion()`, `getClientPath()` lines 92, 94; constructor line 53) | `metadata()` | RETARGET-OBSERVER (*) (2 assertion lines in the helper used by 1-2 tests), RETARGET-SETUP line 53 | 3 (class) |
| LogMessageFlushTaskTest | testStop lines 56, 57, 60, 62, 65, 70, 71 | `getJobs`, `getJobById`, `JobImpl.getActivities` | `jobs().getAll()/get`, `activities().getAll()`; plus `start(njams)` -> `start(njams, sender)` (Task 2). Keep its three tests' assertions | RETARGET-OBSERVER (*) (1 test, 7 assertion lines) | 4 (class) |
| AbstractReceiverTest | mockUp (line 174), testOnInstructionExtendedRequestException (line 287) | `getInstructionListeners` stub | `when(njams.commands()).thenReturn(commandsMock)` and `when(commandsMock.list()).thenReturn(list)` (`NjamsCommands` is a public non-final class) | RETARGET-SETUP | 2 places |
| SharedReceiverSupportTest | mockNjams (line 27) | `getClientPath` stub | stub `njams.metadata().getClientPath()` via mock facet | RETARGET-SETUP | 1 place |
| CommunicationFactoryTest | setUp line 59 | `getClientPath` stub | same | RETARGET-SETUP | 1 place |
| JmsReceiverMock (line 63), SharedJmsReceiverTest (37), SharedReceiverSelectionSpecTest.client (55) | `getClientPath` stub | same | RETARGET-SETUP | 3 places |
| ConfigurationInstructionListenerTest.setUp | `when(njams.getConfiguration()).thenReturn(configuration)` | stub `njams.configuration()` facet with `get()` returning `configuration` (plus `Settings` -> `ClientSettings`) | RETARGET-SETUP + MIGRATE-SETUP | 1 place |
| AbstractReplayHandlerTest | RecordingHandler overrides the 2-arg methods (lines 52, 62) | 3-arg `executeReplay(Path,String,String)`/`testReplay(...)` | executeReplaySuccessReturnsLogIdAndSuccessResponse, testReplayRoutesToTestReplayAndYieldsTestMarkerLogId, missingProcessYieldsErrorResponse, executeReplayExceptionYieldsErrorResponse: change the handler to override the 3-arg methods (fixture only, assertions untouched; `processName` argument identical for requests with a `Process` parameter) | RETARGET-SETUP | 4 |
| AbstractReplayHandlerTest | legacyHandlerReceivesNameWhenProcessPathPresent, legacyHandlerReceivesPathNameWhenOnlyProcessPathPresent | `resolveName` delegation of the 2-arg defaults (name derived from path) | Behaviour disappears with the 2-arg defaults; the 3-arg handler contract is pinned by pathAwareHandlerReceivesResolvedProcessPathAndName, pathAwareHandlerReceivesNullPathWhenServerSendsOnlyName, pathAwareTestReplayReceivesProcessPath | DELETE | 2 |
| AbstractReplayHandlerTest | pathAware* (3) | none | - | KEEP-UNCHANGED | 3 |
| Lifecycle specs using `getSender()` | SameInstanceRestartSpecTest.assertRestartUsesFreshSender (lines 38, 42), SharedSenderSelectionSpecTest (53, 64 in two tests), ReceiverListenerDeregistrationSpecTest (36, `@SuppressWarnings`) | `Njams.getSender()` | package-private `sender()` is not reachable from package `communication.lifecycle`: needs a test accessor `com.im.njams.sdk.SenderProbe` in `njams-sdk/src/test` (**NEW S1**; the plan creates `SenderProbe` only in the comm-it module). Act lines RETARGET-SETUP; assertions `assertNotSame(first, njams.getSender())` (SameInstanceRestart) and `assertSame/NotSame(njamsA.getSender(), njamsB.getSender())` (SharedSenderSelection) contain the accessor | RETARGET-SETUP (ReceiverListenerDeregistration, 1 test) + RETARGET-OBSERVER (*) (3 tests, 5 lines: restart 42, selection 53 x2, 64 x2 counted by occurrence) | 3 tests |
| NjamsSenderTest, SenderDispatchClassificationTest, SenderPoolTestAccess, TestSender, TestReceiver, LifecycleTestTransport, 12 lifecycle spec tests, StartupResultTest, ArgosSenderTest, FileConfigurationProviderTest, ProcessModelTest, CommonBfsModelLayouterTest, PolylineProcessDiagramFactoryTest, TruncatingTest | `Settings` only | `ClientSettings.from(...)` | MIGRATE-SETUP | see 5.3 |
| TruncatingTest | `mock(Njams.class)` without legacy stubs | main code will read facets | needs a check at Task 4: if the mock lacks `metadata()`/`configuration()` stubs, NPE; no hit in the compile output | RISK (see 7) | - |

### 4.16 Settings-layer tests (deleted with the feature, Task 9)

| Class | Tests | Reason | Action | n |
|---|---|---|---|---|
| `settings/SettingsTest` | 14 | tests `Settings` itself | DELETE with the feature | 14 |
| `settings/FileSettingsProviderTest` | 1 | tests `FileSettingsProvider`, `SettingsProviderFactory`, `PROPERTY_SETTINGS_PROVIDER` | DELETE with the feature | 1 |
| `settings/PropertiesFileSettingsProviderTest` | 7 | tests `PropertiesFileSettingsProvider`, the five provider keys | DELETE with the feature | 7 |
| `settings/HierarchicalSettingsTest`, `PropertyUtilTest`, `ReadOnlyClientSettingsTest`, `encoding/*` | - | cover the kept `ClientSettings` stack; confirm that parent-file chaining (`PropertiesFileSettingsProviderTest.testModifiedParentKey`, `testCircleShouldNotBePossible`) is a settings-provider feature that disappears; the nearest kept equivalent is HierarchicalSettingsTest (parent properties) | KEEP-UNCHANGED | - |

Behaviour that disappears with the layer: file-/properties-file-backed settings loading, parent-file chaining and circle detection, provider factory selection by `njams.sdk.settings.provider`. The user-facing replacement is the documented `HierarchicalSettings`/`Properties.load` pattern (Task 9 step 5), not a SDK feature.

### 4.17 Not-listed-in-the-plan classes found by the compile (no extra decision needed)

`JobConcurrencyTest`, `SubProcessActivityImplTest`, `JobAttributesTest` (narrowed only), `ProcessModelTest` (`PROPERTY_SERVER_COMPATIBILITY` stays), `SimpleProcessModelLayouterTest` (keep mark), `JsonSerializerFactoryTest`, `JmsSenderTest` (section E initially postponed, later delivered by Task 11), `HttpClientFactoryClientTest`, `HttpStatusExceptionTest`, `HttpSendExceptionTest`, `CleanTracepointsTaskTest`/`TraceMessageBuilderTest` (`new Integer`) - unrelated deprecations, untouched.

## 5. Other sources

### 5.1 `njams-sdk` tests with Mockito mocks of facets (setup stubs needed)

`mock(Njams.class)` appears in 14 test classes. 11 stub removed getters (rows above). **Not stubbed today and not flagged by the compiler:** `NjamsSenderTest`, `TruncatingTest` - verify at Task 4 that the migrated main code does not start dereferencing a facet of those mocks.

### 5.2 Test support that must be added (not a test)

| ID | What | Why | Plan has it? |
|---|---|---|---|
| S1 | `com.im.njams.sdk.SenderProbe` (or equivalent) in `njams-sdk/src/test`, exposing `Njams.sender()` for tests in other packages | `communication.lifecycle.{SameInstanceRestartSpecTest, SharedSenderSelectionSpecTest, ReceiverListenerDeregistrationSpecTest}` | No (plan only mentions comm-it) |
| S2 | `JobFlushAccess` in package `logmessage` (test) | `NjamsSampleTest` flush calls | Yes (Task 5) |

### 5.3 MIGRATE-SETUP classes (setup only, Task 9)

`njams-sdk` tests (36 classes with `Settings`): AbstractTest, ArgosSenderTest, CommunicationFactoryTest, NjamsSenderTest, SenderDispatchClassificationTest, SenderPoolTestAccess, TestReceiver, TestSender, LifecycleTestTransport, MessageRetentionSpecTest, ReceiverStartGatingSpecTest, SameInstanceRestartSpecTest, SenderDeadlockRegressionTest, SenderExceptionListenerRemovalSpecTest, SenderStartGatingSpecTest, SharedReceiverRestartSpecTest, SharedReceiverSelectionSpecTest, SharedSenderOutageSpecTest, SharedSenderRecoverySignalSpecTest, SharedSenderSelectionSpecTest, StartupResultTest, ConfigurationInstructionListenerTest, FileConfigurationProviderTest, DataMaskingTest, ExtractHandlerTest, JobTest, TruncatingTest, ProcessModelTest, CommonBfsModelLayouterTest, PolylineProcessDiagramFactoryTest, NjamsTest, NjamsSampleTest, JmsClientEndToEndBaselineIT (in `communication/it`), plus the three deleted settings test classes. n = 33 classes (excluding the 3 deleted ones).
`njams-sdk-communication-it`: 23 files import/use `Settings`. Sample modules: see appendix A.

## 6. Policy question and permission requests

### 6.1 The three tests that need explicit permission (as named in the spec)

| Test class | Exact change |
|---|---|
| `NjamsSampleTest` | 2 lines (157, 158): `assertThat(job.getAttribute("json"), ...)` / `("xml")` to `job.attributes().get(...)`; 26 lines (740-833, 996-1089): `assertThat(job.getActivities().size(), is(N))` to `job.activities().getAll().size()`, expected values unchanged. Everything else in the class (flush calls, `addImage`, `setTreeElementType`, `createActivity`, `end()`, `getConfiguration()`, `Settings`) is arrange/act code. |
| `FailedStartupCleanupTest` | 1 line (58): `filter(l -> l == njams)` to `filter(l -> !(l instanceof ConfigurationInstructionListener))`, expected count 1 unchanged. |
| `NjamsJobsTest` | helper `job(...)` additionally stubs `tracing()` with a `JobTracing` mock; 2 lines (88, 101): `verify(job).setDeepTrace(true)` to `verify(tracing).setDeepTrace(true)`. |

### 6.2 Finding: more assertion-text changes than the three, and a question

The three named tests are not the only ones where a kept assertion observes state through a member that is removed. Besides the 31 lines above, the following **kept behaviours** have no equivalent facet test, so under the rule "never change assertions of kept behaviour" they are blocked unless you either allow an accessor-only substitution (RETARGET-OBSERVER) or accept "write a facet copy first, then delete the old one" (NEW N10-N20). Both give the same final test code; the second just makes the copy explicit.

| # | Class | Tests | Assertion lines with removed accessor |
|---|---|---|---|
| 1 | CleanTracepointsTaskTest | testRun, checkTraceMessage helper | 211, 217, 252, 254 |
| 2 | TraceMessageBuilderTest | checkTraceMessage helper | 92, 94 |
| 3 | LogMessageFlushTaskTest | testStop | 56, 57, 60, 62, 65, 70, 71 |
| 4 | ActivityBuilderTest | buildGeneratesInstanceIdAndStartsActivity, setStarterMarksStartActivity | 46, 95 |
| 5 | ActivityImplTest | testOverrideJobAttributesWithActivityAttributes | 161, 163, 165, 166, 170, 171 |
| 6 | JobErrorHandlingTest | commitReaddsActivityThatWasAlreadySent | 107, 113 |
| 7 | JobImplTest | testSetStartActivity, testSetStartActivityAndFlushIt, setMoreStartActivitiesAfterFlushingTheFirstStartActivity | 485, 491, 507, 530 |
| 8 | JobFacadeBaselineTest | recordingAddsNjamsRecordedAttribute | 274 |
| 9 | LimitPayloadTest | testFields | 137, 139 |
| 10 | StartDataLimitTest | 5 tests | 36, 46, 56, 66, 76 |
| 11 | SameInstanceRestartSpecTest / SharedSenderSelectionSpecTest | 3 tests | `njams.getSender()` inside `assertNotSame`/`assertSame` (5 occurrences) |
| 12 | ProcessFilterTest | 19 tests | 75 lines, argument type only (`new common.Path(str)` to `Path.resolve(str)`) |

Total: 40 assertion lines in rows 1-11 (11 classes) plus the 75 argument-only lines of row 12. My recommendation: allow the accessor-only substitution for rows 1-11 and the argument conversion for row 12 as one blanket decision (no expected value changes), instead of writing 15+ copies. Needs your decision before Tasks 4-7b; until decided, none of these rows is touched.

## 7. Risks found while mapping

1. `NjamsJobsTest`, `AbstractReceiverTest`, `SharedReceiverSupportTest`, `ConfigurationInstructionListenerTest`, `JobRuntimeConfigTest`, `CleanTracepointsTaskTest` use Mockito mocks of `Njams`/`Job`; after the migration the main code calls facets (`metadata()`, `commands()`, `configuration()`, `tracing()`), which return `null` from an unstubbed mock. A forgotten stub does not fail at compile time and, inside `ExceptionSupport.suppressException`, would not even fail at runtime (the exception is swallowed and the later `verify` fails instead).
2. `ExtractHandlerTest` replaces the configuration with `doReturn(conf).when(spy).getConfiguration()`. The facet-based equivalent must make `njams.configuration().get()` and `njams.configuration().isExcluded(...)`/`getLogMode()` consistent (`JobRuntimeConfig` reads both). Plan Task 4 step 2 says "behavior for mocked Njams handled in step 4"; this is the concrete hot spot.
3. `JobImplTest.testDataMaskingAfterFlushing` currently injects the sender through `when(spyNjams.getSender())`. After Task 2 the flusher reads the sender from the `LogMessageFlushTask` registry, so a spy cannot inject it. Use `TestSender.setSenderMock(...)` (testing-conventions) and keep `checkAllFields()` as is.
4. `ProcessFilterTest` conversion: `Path.resolve` drops empty segments while `common.Path` may keep them; verify each literal produces the same `toString()` before converting.
5. `NjamsFacadeBaselineTest.getSenderReturnsNonNullAndIsCached` and the lifecycle/comm-it tests need a test accessor (S1) because `sender()` is package-private.
6. Reduced baseline/facet tests keep names that mention removed API (`..._viaFacet`, `deprecated...`): renaming is optional and not an assertion change.

## 8. NEW TEST NEEDED

| ID | Class (package) | What | Replaces |
|---|---|---|---|
| N1 | NjamsTest (`sdk`) | Two `Njams` with equal client paths are equal with equal hash codes; different path not equal; comparison with a Mockito mock of `Njams` and with another type returns false without NPE (plan Task 6 step 1) | NjamsFacadeBaselineTest.equalsAndHashCodeAreBasedOnClientPath |
| N2 | NjamsCommandsTest or NjamsTest (`sdk`) | `commands().dispatch(PING)` answers `resultCode 0`, message `Pong`, parameters `clientId` = session id and `category` | pingInstructionIsAnswered |
| N3 | same | `dispatch(GET_REQUEST_HANDLER)` returns `clientId` | getRequestHandlerInstructionReturnsClientId |
| N4 | same | `dispatch` of an unknown command returns `resultCode 1` | unsupportedCommandIsRejected |
| N5 | NjamsFacetApiTest | `serializers().remove` returns the registered serializer, default behaviour is restored, a second `remove` and `remove(null)` return null | removeSerializerReturnsTheRegisteredOneAndRestoresDefault |
| N6 | NjamsTest (`sdk`) | package-private `sender()` is non-null and cached (same instance on repeated calls) | getSenderReturnsNonNullAndIsCached |
| N7 | NFAT | after `jobs().remove(id)`, `jobs().getAll()` is empty | jobLifecycleAfterStart |
| N8 | NjamsModelTest | `model().has((Path) null)` is false | testNoProcessModelForNullPath |
| N9 | JobFacetApiTest | `attributes().add` on a never-started job works, survives a flush of the never-started job, and is readable via `get`/`getAll` | testAddAttributeWithoutStart, testAddAttributeFlushAndGetAttribute |
| N10 | conditional (only if the 6.2 policy is "copy, then delete") | facet copies for the 40 assertion lines (rows 1-11) | RETARGET-OBSERVER rows |

Support: S1 `SenderProbe` (test accessor), S2 `JobFlushAccess` (already in the plan). Tests planned by the plan itself (Tasks 2, 3, 5, 6, 8) are not repeated here.

## 9. Summary

### 9.1 Counts per action (test methods unless stated)

DELETE (a named existing replacement or disappearing behaviour):

| Source | n |
|---|---|
| NjamsFacadeBaselineTest (48, of which 6 only after N1-N9 are written) | 48 |
| NjamsTest | 11 + 1 (after N8) = 12 (testSerializer, 2 serialize, 2 hasProcessModel, defaultLayouter, 6 pattern, + null-path) |
| JobFacadeBaselineTest | 17 |
| JobFacetApiTest | 2 |
| JobActivitiesTest | 2 |
| JobImplTest | 1 + 1 + 2 + 2 + 1 = 7 (addActivityWithoutStart, addAttributeWithStart, 2 after N9, 2 byInstanceId, getActivities) |
| JobInstrumentedTest | 2 |
| PathTest | 6 |
| common/PathTest | 10 |
| ConfigurationPathOverloadsTest | 9 |
| DataMaskingTest | 1 |
| AbstractReplayHandlerTest | 2 |
| NjamsFacetApiTest | 2 |
| Settings tests (SettingsTest 14, FileSettingsProviderTest 1, PropertiesFileSettingsProviderTest 7) | 22 |
| **Total DELETE** | **142** |

REDUCE: ConfigurationPathOverloadsTest 4, JobActivitiesTest 5, JobFacetApiTest 1, NjamsFacetApiTest 1 = **11**.

RETARGET-SETUP (test methods/places, helpers counted as one): NjamsFacetApiTest 1, NjamsTest 9, NjamsSampleTest 8 tests, JobFacadeBaselineTest 5, JobImplTest 17 (incl. 3 helper-driven), AbstractTest helpers 3, ActivityBuilderTest 5, ActivityFlagTruncation 3, ActivityImplExtractData 3, DataMasking 1 (stub), ExtractHandlerTest 2, GroupImplTest 1, JobErrorHandling 1 helper, JobRuntimeConfigTest 1, JobTest 1, JobTruncation/StartDataOncePerJob 2 helpers, LimitPayload 1, CleanTracepointsTaskTest 13, TraceMessageBuilderTest 3, LogMessageFlushTaskTest 4, AbstractReceiverTest 2, SharedReceiverSupport/CommunicationFactory/JmsReceiverMock/SharedJmsReceiver/SharedReceiverSelectionSpec 5 places, ConfigurationInstructionListenerTest 1, AbstractReplayHandlerTest 4, ReceiverListenerDeregistrationSpecTest 1 = approx **108**.

RETARGET-ARG: ProcessFilterTest 19.

RETARGET-OBSERVER (assertion accessor, decision 6.2): 3 classes with permission (NjamsSampleTest 2 tests with 28 lines, FailedStartupCleanupTest 1 test, NjamsJobsTest 2 tests) + 11 classes without explicit permission yet (rows 1-11 of 6.2: 18 tests, 40 lines).

MIGRATE-SETUP: 33 `njams-sdk` test classes, 23 comm-it files, 9 sample-client files + sample-app (appendix A).

KEEP-UNCHANGED: the rest (about 1160 of the 1567 tests are not touched).

### 9.2 NEW TEST NEEDED

N1-N9 (section 8), N10 conditional; test support S1 (not in plan) and S2 (in plan).

### 9.3 Tests that need explicit permission

1. `NjamsSampleTest`: 28 assertion lines (2 x `getAttribute`, 26 x `getActivities().size()`), accessor only.
2. `FailedStartupCleanupTest`: 1 assertion line (58), predicate only.
3. `NjamsJobsTest`: 2 verify lines (88, 101) plus a `tracing()` stub in the helper.
Details in 6.1. Additional decision requested in 6.2 for 11 more classes.

## Appendix A - sample modules (compile with deprecation)

| File | Removed API used |
|---|---|
| sample-client `GroupClient` (141), `SettingsFromFileClient` (130), `SimpleEndlessClient` (115), `SubProcessClient` (133), `SubProcessSpawnedClient` (117, 124) | `Job.end()` -> `end(true)` |
| sample-client `SettingsFromFileClient` (54-66) | `PROPERTY_SETTINGS_PROVIDER`, `PROPERTY_PROPERTIES_FILE_SETTINGS_FILE`, `PropertiesFileSettingsProvider`, `SettingsProviderFactory`, `Settings` -> rewrite with `Properties.load` + `ClientSettings.from` |
| sample-client `AdditionalProcessClient`, `GroupClient`, `SimpleClient`, `SimpleEndlessClient`, `SubProcessClient`, `SubProcessSpawnedClient`, `argos/JVMSenderClient`, `argos/RandomNumberSenderClient` | `Settings` -> `ClientSettings` |
| sample-app `LogMessageResource` (49, 57, 67) | `Njams.getJobById`, `Job.getActivityByModelId`, `Job.end()` |
| sample-app `NjamsStartup` (70) | `Njams.getClientPath` (and `Settings` use) |

The plan's Task 7 list additionally names `GroupClient`, `SettingsFromFileClient`, `SimpleEndlessClient`, `SubProcessClient`, `SubProcessSpawnedClient`; the compile adds `argos/JVMSenderClient` and `argos/RandomNumberSenderClient` (Settings only).

## Appendix B - `njams-sdk-communication-it` (test sources, grep)

1. `HttpRepeatedStartStopLeakIT` (lines 46-82) and `PoolBookkeepingIT` (61, 88): `Njams.getSender()` (already `@SuppressWarnings("deprecation")`): `SenderProbe` as in plan Task 2.
2. 23 files use `com.im.njams.sdk.settings.Settings` (MIGRATE-SETUP, Task 9).
3. No use of `Job.end()` or the legacy `Njams` getters other than the two above; `MessageDriver` uses `activity.end()` (kept).
4. `JmsClientEndToEndBaselineIT` in `njams-sdk/src/test/.../communication/it` (not the comm-it module): `Job.createActivity` (74), `Job.end()` (75), `Settings` (32-33) - RETARGET-SETUP/MIGRATE-SETUP.
