/**
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2019 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) of the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */
package spoon.test.prettyprinter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import spoon.Launcher;
import spoon.SpoonException;
import spoon.refactoring.Refactoring;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThrow;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtModifiable;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.modelobs.ChangeCollector;
import spoon.support.modelobs.SourceFragmentCreator;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import spoon.test.prettyprinter.testclasses.OneLineMultipleVariableDeclaration;
import spoon.test.prettyprinter.testclasses.Throw;
import spoon.test.prettyprinter.testclasses.InvocationReplacement;
import spoon.test.prettyprinter.testclasses.ToBeChanged;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static spoon.test.SpoonTestHelpers.assumeNotWindows;

public class TestSniperPrinter {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testClassRename1() throws Exception {
		// contract: one can sniper out of the box after Refactoring.changeTypeName
		testClassRename(type -> {
			Refactoring.changeTypeName(type, "Bar");
		});
	}

	@Test
	public void testClassRename2() throws Exception {
		// contract: one can sniper after setSimpleName
		// with the necessary tweaks
		testClassRename(type -> {
			type.setSimpleName("Bar");
			type.getFactory().CompilationUnit().addType(type);
		});

	}

	public void testClassRename(Consumer<CtType<?>> renameTransfo) throws Exception {
		// contract: sniper supports class rename
		String testClass = ToBeChanged.class.getName();
		Launcher launcher = new Launcher();
		launcher.addInputResource(getResourcePath(testClass));
		launcher.getEnvironment().setPrettyPrinterCreator(() -> {
			return new SniperJavaPrettyPrinter(launcher.getEnvironment());
		});
		launcher.setBinaryOutputDirectory(folder.newFolder());
		launcher.buildModel();
		Factory f = launcher.getFactory();

		final CtClass<?> type = f.Class().get(testClass);

		// performing the type rename
		renameTransfo.accept(type);
		//print the changed model
		launcher.prettyprint();


		String contentOfPrettyPrintedClassFromDisk = getContentOfPrettyPrintedClassFromDisk(type);
		assertTrue(contentOfPrettyPrintedClassFromDisk, contentOfPrettyPrintedClassFromDisk.contains("EOLs*/ Bar<T, K>"));

	}


	@Test
	public void testPrintInsertedThrow() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		testSniper(Throw.class.getName(), type -> {
			CtConstructorCall<?> ctConstructorCall = (CtConstructorCall<?>) type.getMethodsByName("foo").get(0).getBody().getStatements().get(0);
			CtThrow ctThrow = type.getFactory().createCtThrow(ctConstructorCall.toString());
			ctConstructorCall.replace(ctThrow);
		}, (type, printed) -> {
			assertIsPrintedWithExpectedChanges(type, printed,
					"\\Qvoid foo(int x) {\n"
					+ "\t\tnew IllegalArgumentException(\"x must be nonnegative\");\n"
					+ "\t}",
					"void foo(int x) {\n"
					+ "\t\tthrow new java.lang.IllegalArgumentException(\"x must be nonnegative\");\n"
					+ "\t}");
		});
	}

	@Test
	public void testPrintReplacementOfInvocation() {
		testSniper(InvocationReplacement.class.getName(), type -> {
			CtLocalVariable<?> localVariable = (CtLocalVariable<?>) type.getMethodsByName("main").get(0).getBody().getStatements().get(0);
			CtInvocation<?> invocation = (CtInvocation<?>) localVariable.getAssignment();
			CtExpression<?> prevTarget = invocation.getTarget();
			CtCodeSnippetExpression<?> newTarget = type.getFactory().Code().createCodeSnippetExpression("Arrays");
			CtType<?> arraysClass = type.getFactory().Class().get(Arrays.class);
			CtMethod<?> method = (CtMethod<?>) arraysClass.getMethodsByName("toString").get(0);
			CtExecutableReference<?> refToMethod = type.getFactory().Executable().createReference(method);
			CtInvocation<?> newInvocation = type.getFactory().Code().createInvocation(newTarget, refToMethod, prevTarget);
			invocation.replace(newInvocation);
		}, (type, printed) -> {
			assertIsPrintedWithExpectedChanges(type, printed, "\\QString argStr = args.toString();", "String argStr = Arrays.toString(args);");
		});
	}

	@Test
	public void testPrintLocalVariableDeclaration() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		// contract: joint local declarations can be sniper-printed in whole unmodified method
		testSniper(OneLineMultipleVariableDeclaration.class.getName(), type -> {
			type.getFields().stream().forEach(x -> { x.delete(); });
		}, (type, printed) -> {
			assertEquals("package spoon.test.prettyprinter.testclasses;\n"
					+	"\n"
					+	"public class OneLineMultipleVariableDeclaration {\n"
					+	"\n"
					+	"\tvoid foo(int a) {\n"
					+ "\t\tint b = 0, e = 1;\n"
					+ "\t\ta = a;\n"
					+	"\t}\n"
					+	"}", printed);
		});
	}

	@Test
	public void testPrintLocalVariableDeclaration2() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		// contract: joint local declarations can be sniper-printed
		testSniper(OneLineMultipleVariableDeclaration.class.getName(), type -> {
			type.getElements(new TypeFilter<>(CtLocalVariable.class)).get(0).delete();
		}, (type, printed) -> {
			assertEquals("package spoon.test.prettyprinter.testclasses;\n"
					+	"\n"
					+	"public class OneLineMultipleVariableDeclaration {int a;\n"
					+ "\n"
					+	"\tint c;\n"
					+	"\n"
					+ "\tvoid foo(int a) {int e = 1;\n"
					+ "\t\ta = a;\n"
					+ "\t}\n"
					+	"}", printed);
		});
	}

	@Test
	public void testPrintOneLineMultipleVariableDeclaration() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		// contract: files with joint field declarations can be recompiled after sniper
		testSniper(OneLineMultipleVariableDeclaration.class.getName(), type -> {
			// we change something (anything would work)
			type.getMethodsByName("foo").get(0).delete();
		}, (type, printed) -> {
			assertEquals("package spoon.test.prettyprinter.testclasses;\n"
					+	"\n"
					+	"public class OneLineMultipleVariableDeclaration {int a;\n"
					+	"\n"
					+ "\tint c;\n"
					+ "}", printed);
		});
	}

	@Test
	public void testPrintUnchaged() {
		//contract: sniper printing of unchanged compilation unit returns origin sources
		testSniper(ToBeChanged.class.getName(), type -> {
			//do not change the model
		}, (type, printed) -> {
			assertIsPrintedWithExpectedChanges(type, printed);
		});
	}

	@Test
	public void testPrintAfterRenameOfField() {
		//contract: sniper printing after rename of field
		testSniper(ToBeChanged.class.getName(), type -> {
			//change the model
			type.getField("string").setSimpleName("modified");
		}, (type, printed) -> {
			// everything is the same but the field name
			assertIsPrintedWithExpectedChanges(type, printed, "\\bstring\\b", "modified");
		});
	}

	@Test
	public void testPrintChangedComplex() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		//contract: sniper printing after remove of statement from nested complex `if else if ...`
		testSniper("spoon.test.prettyprinter.testclasses.ComplexClass", type -> {
			//find to be removed statement "bounds = false"
			CtStatement toBeRemoved = type.filterChildren((CtStatement stmt) -> stmt.getPosition().isValidPosition() && stmt.getPosition().getLine() == 231).first();

			// check that we have picked the right statement
			ChangeCollector.runWithoutChangeListener(type.getFactory().getEnvironment(), () -> {
				assertEquals("bounds = false", toBeRemoved.toStringDebug());
			});
			//change the model
			toBeRemoved.delete();
		}, (type, printed) -> {
			assertIsPrintedWithExpectedChanges(type, printed, "\\QNO_SUPERINTERFACES) {\n\\E\\s*bounds\\s*=\\s*false;\n", "NO_SUPERINTERFACES) {\n");
		});
	}

	@Test
	public void testPrintAfterRemoveOfFirstParameter() {
		//contract: sniper print after remove of first parameter
		testSniper(ToBeChanged.class.getName(), type -> {
			//delete first parameter of method `andSomeOtherMethod`
			type.getMethodsByName("andSomeOtherMethod").get(0).getParameters().get(0).delete();
		}, (type, printed) -> {
			assertIsPrintedWithExpectedChanges(type, printed, "\\s*int\\s*param1,", "");
		});
	}

	@Test
	public void testSimple() {
		//contract: sniper print after remove of last statement
		testSniper(spoon.test.prettyprinter.testclasses.Simple.class.getName(), type -> {
			//delete first parameter of method `andSomeOtherMethod`
			type.getMethodsByName("andSomeOtherMethod").get(0).getBody().getStatements().get(1).delete();
		}, (type, printed) -> {
			assertIsPrintedWithExpectedChanges(type, printed, "\\s*System.out.println\\(\"bbb\"\\);", "");
		});
	}

	@Test
	public void testPrintAfterRemoveOfMiddleParameter() {
		//contract: sniper print after remove of middle (not first and not last) parameter
		testSniper(ToBeChanged.class.getName(), type -> {
			//delete second parameter of method `andSomeOtherMethod`
			type.getMethodsByName("andSomeOtherMethod").get(0).getParameters().get(1).delete();
		}, (type, printed) -> {
			assertIsPrintedWithExpectedChanges(type, printed, "\\s*String\\s*param2\\s*,", "");
		});
	}

	@Test
	public void testPrintAfterRemoveOfLastParameter() {
		//contract: sniper print after remove of last parameter
		testSniper(ToBeChanged.class.getName(), type -> {
			//delete last parameter of method `andSomeOtherMethod`
			type.getMethodsByName("andSomeOtherMethod").get(0).getParameters().get(2).delete();
		}, (type, printed) -> {
			assertIsPrintedWithExpectedChanges(type, printed, "\\s*, \\QList<?>[][]... twoDArrayOfLists\\E", "");
		});
	}

	@Test
	public void testPrintAfterRemoveOfLastTypeMember() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		//contract: sniper print after remove of last type member - check that suffix spaces are printed correctly
		testSniper(ToBeChanged.class.getName(), type -> {
			//delete first parameter of method `andSomeOtherMethod`
			type.getField("twoDArrayOfLists").delete();
		}, (type, printed) -> {
			assertIsPrintedWithExpectedChanges(type, printed, "\\Q\tList<?>[][] twoDArrayOfLists = new List<?>[7][];\n\\E", "");
		});
	}

	@Test
	public void testPrintAfterAddOfLastTypeMember() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		//contract: sniper print after add of last type member - check that suffix spaces are printed correctly
		class Context {
			CtField<?> newField;
		}
		Context context = new Context();

		testSniper(ToBeChanged.class.getName(), type -> {
			Factory f = type.getFactory();
			//create new type member
			context.newField = f.createField(type, Collections.singleton(ModifierKind.PRIVATE), f.Type().DATE, "dateField");
			type.addTypeMember(context.newField);
		}, (type, printed) -> {
			String lastMemberString = "new List<?>[7][];";
			assertIsPrintedWithExpectedChanges(type, printed, "\\Q" + lastMemberString + "\\E", lastMemberString + "\n\n\t" + context.newField.toStringDebug());
		});
	}

	@Test
	public void testPrintAfterRemoveOfFormalTypeParamsAndChangeOfReturnType() {
		//contract: sniper printing after remove of formal type parameters and change of return type
		testSniper(ToBeChanged.class.getName(), type -> {
			//change the model
			CtMethod<?> m = type.getMethodsByName("andSomeOtherMethod").get(0);
			m.setFormalCtTypeParameters(Collections.emptyList());
			m.setType((CtTypeReference) m.getFactory().Type().stringType());
		}, (type, printed) -> {
			// everything is the same but method formal type params and return type
			assertIsPrintedWithExpectedChanges(type, printed, "\\Qpublic <T, K> void andSomeOtherMethod\\E", "public java.lang.String andSomeOtherMethod");
		});
	}

	@Test
	public void testPrintTypesProducesFullOutputForSingleTypeCompilationUnit() {
		// contract: printTypes() should produce the same output as launcher.prettyprint() for a
		// single-type compilation unit

		// there is no particular reason for using the YamlRepresenter resource, it simply already
		// existed and filled the role it needed to
		String resourceName = "visibility.YamlRepresenter";
		String inputPath = getResourcePath(resourceName);

		Launcher printTypesLauncher = createLauncherWithSniperPrinter();
		printTypesLauncher.addInputResource(inputPath);
		printTypesLauncher.buildModel();
		String printTypesString = printTypesLauncher.createPrettyPrinter()
				.printTypes(printTypesLauncher.getModel().getAllTypes().toArray(new CtType[0]));

		testSniper(resourceName, ctType -> {}, (type, prettyPrint) -> {
			assertEquals(prettyPrint, printTypesString);
		});
	}

	@Test
	public void testPrintTypesThrowsWhenPassedTypesFromMultipleCompilationUnits() {
		// contract: printTypes() should raise an IllegalArgumentException if it is passed types
		// from multiple CUs

		Launcher launcher = createLauncherWithSniperPrinter();
		// there is no particular reason for the choice of these two resources, other than that
		// they are different from each other and existed at the time of writing this test
		launcher.addInputResource(getResourcePath("visibility.YamlRepresenter"));
		launcher.addInputResource(getResourcePath("spoon.test.variable.Tacos"));
		CtType<?>[] types = launcher.buildModel().getAllTypes().toArray(new CtType<?>[0]);

		try {
			launcher.getEnvironment().createPrettyPrinter().printTypes(types);
			fail("Expected an IllegalArgumentException");
		} catch (IllegalArgumentException e) {
		    // pass
		}
	}

	@Test
	public void testCalculateCrashesWithInformativeMessageWhenSniperPrinterSetAfterModelBuild() {
		// contract: The sniper printer must be set before building the model, and the error message
		// one gets when this has not been done should say so.

		Launcher launcher = new Launcher();
		launcher.addInputResource(getResourcePath("visibility.YamlRepresenter"));

		// build model, then set sniper
		launcher.buildModel();
		launcher.getEnvironment().setPrettyPrinterCreator(
				() -> new SniperJavaPrettyPrinter(launcher.getEnvironment()));

		CtCompilationUnit cu = launcher.getFactory().CompilationUnit().getMap().values().stream()
				.findFirst().get();

		// crash because sniper was set after model was built, and so the ChangeCollector was not
		// attached to the environment
		try {
			launcher.createPrettyPrinter().calculate(cu, cu.getDeclaredTypes());
		} catch (SpoonException e) {
			assertThat(e.getMessage(), containsString(
					"This typically means that the Sniper printer was set after building the model."));
			assertThat(e.getMessage(), containsString(
					"It must be set before building the model."));
		}
	}

	@Test
	public void testNewlineInsertedBetweenCommentAndTypeMemberWithAddedModifier() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		// contract: newline must be inserted after comment when a succeeding type member has had a
		// modifier added to it

		Consumer<CtType<?>> addModifiers = type -> {
			type.getField("NON_FINAL_FIELD")
					.addModifier(ModifierKind.FINAL);
			type.getMethod("nonStaticMethod").addModifier(ModifierKind.STATIC);
			type.getNestedType("NonStaticInnerClass").addModifier(ModifierKind.STATIC);
		};
		BiConsumer<CtType<?>, String> assertCommentsCorrectlyPrinted = (type, result) -> {
		    assertThat(result, containsString("// field comment\n"));
			assertThat(result, containsString("// method comment\n"));
			assertThat(result, containsString("// nested type comment\n"));
		};

		testSniper("TypeMemberComments", addModifiers, assertCommentsCorrectlyPrinted);
	}

	@Test
	public void testNewlineInsertedBetweenCommentAndTypeMemberWithRemovedModifier() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		// contract: newline must be inserted after comment when a succeeding field has had a
		// modifier removed from it

		Consumer<CtType<?>> removeModifier = type -> {
			// we only test removing a modifier from the field in this test, as removing the
			// last modifier leads to a different corner case where the comment disappears
			// altogether
			type.getField("NON_FINAL_FIELD")
					.removeModifier(ModifierKind.PUBLIC);
		};

		BiConsumer<CtType<?>, String> assertCommentCorrectlyPrinted = (type, result) -> {
			assertThat(result, containsString("// field comment\n"));
		};

		testSniper("TypeMemberComments", removeModifier, assertCommentCorrectlyPrinted);
	}

	@Test
	public void testNewlineInsertedBetweenModifiedCommentAndTypeMemberWithAddedModifier() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		// contract: newline must be inserted after modified comment when a succeeding type member
		// has had its modifier list modified. We test modified comments separately from
		// non-modified comments as they are handled differently in the printer.

		final String commentContent = "modified comment";

		Consumer<CtType<?>> enactModifications = type -> {
			CtField<?> field = type.getField("NON_FINAL_FIELD");
			field.addModifier(ModifierKind.FINAL);
			field.getComments().get(0).setContent(commentContent);
		};

		BiConsumer<CtType<?>, String> assertCommentCorrectlyPrinted = (type, result) -> {
			assertThat(result, containsString("// " + commentContent + "\n"));
		};

		testSniper("TypeMemberComments", enactModifications, assertCommentCorrectlyPrinted);
	}

	@Test
	public void testTypeMemberCommentDoesNotDisappearWhenAllModifiersAreRemoved() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		// contract: A comment on a field should not disappear when all of its modifiers are removed.

		Consumer<CtType<?>> removeTypeMemberModifiers = type -> {
			type.getField("NON_FINAL_FIELD").setModifiers(Collections.emptySet());
			type.getMethodsByName("nonStaticMethod").get(0).setModifiers(Collections.emptySet());
			type.getNestedType("NonStaticInnerClass").setModifiers(Collections.emptySet());
		};

		BiConsumer<CtType<?>, String> assertFieldCommentPrinted = (type, result) ->
			assertThat(result, allOf(
						containsString("// field comment\n    int NON_FINAL_FIELD"),
						containsString("// method comment\n    void nonStaticMethod"),
						containsString("// nested type comment\n    class NonStaticInnerClass")
					)
			);

		testSniper("TypeMemberComments", removeTypeMemberModifiers, assertFieldCommentPrinted);
	}

	@Test
	public void testAddedImportStatementPlacedOnSeparateLineInFileWithoutPackageStatement() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		// contract: newline must be inserted between import statements when a new one is added

		Consumer<CtType<?>> addArrayListImport = type -> {
			Factory factory = type.getFactory();
			assertTrue("there should be no package statement in this test file", type.getPackage().isUnnamedPackage());
			CtCompilationUnit cu = factory.CompilationUnit().getOrCreate(type);
			CtTypeReference<?> arrayListRef = factory.Type().get(java.util.ArrayList.class).getReference();
			cu.getImports().add(factory.createImport(arrayListRef));
		};
		BiConsumer<CtType<?>, String> assertImportsPrintedCorrectly = (type, result) -> {
			assertThat(result, anyOf(
					containsString("import java.util.Set;\nimport java.util.ArrayList;\n"),
					containsString("import java.util.ArrayList;\nimport java.util.Set;\n")));
		};

		testSniper("ClassWithSingleImport", addArrayListImport, assertImportsPrintedCorrectly);
	}

	@Test
	public void testAddedImportStatementPlacedOnSeparateLineInFileWithPackageStatement() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		// contract: newline must be inserted both before and after a new import statement if ther
		// is a package statement in the file

		Consumer<CtType<?>> addArrayListImport = type -> {
			Factory factory = type.getFactory();
			assertFalse("there should be a package statement in this test file", type.getPackage().isUnnamedPackage());
			CtCompilationUnit cu = factory.CompilationUnit().getOrCreate(type);
			CtTypeReference<?> arrayListRef = factory.Type().get(java.util.ArrayList.class).getReference();
			cu.getImports().add(factory.createImport(arrayListRef));
		};
		BiConsumer<CtType<?>, String> assertImportsPrintedCorrectly = (type, result) -> {
			assertThat(result, containsString("\nimport java.util.ArrayList;\n"));
		};

		testSniper("visibility.YamlRepresenter", addArrayListImport, assertImportsPrintedCorrectly);
	}

	@Test
	public void testAddedElementsIndentedWithAppropriateIndentationStyle() {
		assumeNotWindows(); // FIXME Make test case pass on Windows
		// contract: added elements in a source file should be indented with the same style of
		// indentation as in the rest of the file

		Consumer<CtType<?>> addElements = type -> {
		    Factory fact = type.getFactory();
		    fact.createField(type, new HashSet<>(), fact.Type().INTEGER_PRIMITIVE, "z", fact.createLiteral(3));
		    type.getMethod("sum").getBody()
					.addStatement(0, fact.createCodeSnippetStatement("System.out.println(z);"));
		};
		BiConsumer<CtType<?>, String> assertTabs = (type, result) -> {
			assertThat(result, containsString("\n\tint z = 3;"));
			assertThat(result, containsString("\n\t\tSystem"));
		};
		BiConsumer<CtType<?>, String> assertTwoSpaces = (type, result) -> {
		    assertThat(result, containsString("\n  int z = 3;"));
		    assertThat(result, containsString("\n    System"));
		};
		BiConsumer<CtType<?>, String> assertFourSpaces = (type, result) -> {
			assertThat(result, containsString("\n    int z = 3;"));
			assertThat(result, containsString("\n        System"));
		};

		testSniper("indentation.Tabs", addElements, assertTabs);
		testSniper("indentation.TwoSpaces", addElements, assertTwoSpaces);
		testSniper("indentation.FourSpaces", addElements, assertFourSpaces);
	}

	@Test
	public void testAddedElementsIndentedWithAppropriateIndentationStyleWhenOnlyOneTypeMemberExists() {
		// contract: added elements in a source file should be indented with the same style of
		// indentation as the single type member, when there is only one type member.

		Consumer<CtType<?>> addElement = type -> {
			Factory fact = type.getFactory();
			fact.createField(type, new HashSet<>(), fact.Type().INTEGER_PRIMITIVE, "z", fact.createLiteral(2));
		};
		final String newField = "int z = 2;";

		BiConsumer<CtType<?>, String> assertTabs = (type, result) ->
				assertThat(result, containsString("\n\t" + newField));
		BiConsumer<CtType<?>, String> assertTwoSpaces = (type, result) ->
				assertThat(result, containsString("\n  " + newField));
		BiConsumer<CtType<?>, String> assertFourSpaces = (type, result) ->
				assertThat(result, containsString("\n    " + newField));

		testSniper("indentation.singletypemember.Tabs", addElement, assertTabs);
		testSniper("indentation.singletypemember.TwoSpaces", addElement, assertTwoSpaces);
		testSniper("indentation.singletypemember.FourSpaces", addElement, assertFourSpaces);
	}

	@Test
	public void testDefaultsToSingleTabIndentationWhenThereAreNoTypeMembers() {
		// contract: if there are no type members in a compilation unit, the sniper printer defaults
		// to indenting with 1 tab

		Consumer<CtType<?>> addField = type -> {
			Factory fact = type.getFactory();
			fact.createField(type, new HashSet<>(), fact.Type().INTEGER_PRIMITIVE, "z", fact.createLiteral(3));
		};
		testSniper("indentation.NoTypeMembers", addField, (type, result) -> {
			assertThat(result, containsString("\n\tint z = 3;"));
		});
	}

	@Test
	public void testOptimizesParenthesesForAddedNestedOperators() {
		// contract: The sniper printer should optimize parentheses for newly inserted elements

		// without parentheses optimization, the expression will be printed as `(1 + 2) + (-(2 + 3))`
		String declaration = "int a = 1 + 2 + -(2 + 3)";
		Launcher launcher = new Launcher();
		CtStatement nestedOps = launcher.getFactory().createCodeSnippetStatement(declaration).compile();

		Consumer<CtType<?>> addNestedOperator = type -> {
			CtMethod<?> method = type.getMethodsByName("main").get(0);
			method.getBody().addStatement(nestedOps);
		};
		BiConsumer<CtType<?>, String> assertCorrectlyPrinted =
				(type, result) -> assertThat(result, containsString(declaration));

		testSniper("methodimport.ClassWithStaticMethod", addNestedOperator, assertCorrectlyPrinted);
	}


	@Test
	public void testPrintTypeWithMethodImportAboveMethodDefinition() {
		// contract: The type references of a method import (e.g. its return type) has source
		// positions in the file the method was imported from. The resolved source end position
		// of the import should not be affected by the placement of the imported method. This
		// test ensures this is the case even when the end position of the imported method is
		// greater than the end position of the import statement.

		Launcher launcher = createLauncherWithSniperPrinter();
		launcher.addInputResource(getResourcePath("methodimport.ClassWithStaticMethod"));
		launcher.addInputResource(getResourcePath("methodimport.MethodImportAboveImportedMethod"));

		CtModel model = launcher.buildModel();
		CtType<?> classWithStaticMethodImport = model.getAllTypes().stream()
				.filter(type -> type.getSimpleName().endsWith("AboveImportedMethod"))
				.findFirst()
				.get();

		List<CtImport> imports = classWithStaticMethodImport.getFactory().CompilationUnit().getOrCreate(classWithStaticMethodImport).getImports();

		String output = launcher
				.getEnvironment()
				.createPrettyPrinter().printTypes(classWithStaticMethodImport);

		assertThat(output, containsString("import static methodimport.ClassWithStaticMethod.staticMethod;"));
	}

	@Test
	public void testPrintTypeWithMethodImportBelowMethodDefinition() {
		// contract: The type references of a method import (e.g. its return type) has source
		// positions in the file the method was imported from. The resolved source start position
		// of the import should not be affected by the placement of the imported method. This
		// test ensures this is the case even when the start position of the imported method is
		// less than the start position of the import statement.

		Launcher launcher = createLauncherWithSniperPrinter();
		launcher.addInputResource(getResourcePath("methodimport.ClassWithStaticMethod"));
		launcher.addInputResource(getResourcePath("methodimport.MethodImportBelowImportedMethod"));

		CtModel model = launcher.buildModel();
		CtType<?> classWithStaticMethodImport = model.getAllTypes().stream()
				.filter(type -> type.getSimpleName().endsWith("BelowImportedMethod"))
				.findFirst()
				.get();

		String output = launcher
				.getEnvironment()
				.createPrettyPrinter().printTypes(classWithStaticMethodImport);

		assertThat(output, containsString("import static methodimport.ClassWithStaticMethod.staticMethod;"));
	}

	@Test
	public void testThrowsWhenTryingToPrintSubsetOfCompilationUnitTypes() {
		// contract: Printing a subset of a compilation unit's types is a hassle to implement at the time of writing
		// this, as a) DJPP will replace the compilation unit with a clone, and b) it makes it more difficult to
		// match source code fragments. For now, we're lazy and simply don't allow it.

		Launcher launcher = createLauncherWithSniperPrinter();
		launcher.addInputResource(getResourcePath("MultipleTopLevelTypes"));

		CtModel model = launcher.buildModel();
		CtType<?> primaryType = model.getAllTypes().stream().filter(CtModifiable::isPublic).findFirst().get();
		CtCompilationUnit cu = primaryType.getFactory().CompilationUnit().getOrCreate(primaryType);
		SniperJavaPrettyPrinter sniper = (SniperJavaPrettyPrinter) launcher.getEnvironment().createPrettyPrinter();

		assertThrows(IllegalArgumentException.class, () -> sniper.calculate(cu, Collections.singletonList(primaryType)));
	}

	/**
	 * 1) Runs spoon using sniper mode,
	 * 2) runs `typeChanger` to modify the code,
	 * 3) runs `resultChecker` to check if sources printed by sniper printer are as expected
	 * @param testClass a file system path to test class
	 * @param transformation a code which changes the Spoon model
	 * @param resultChecker a code which checks that printed sources are as expected
	 */
	private void testSniper(String testClass, Consumer<CtType<?>> transformation, BiConsumer<CtType<?>, String> resultChecker) {
		Launcher launcher = createLauncherWithSniperPrinter();
		launcher.addInputResource(getResourcePath(testClass));
		launcher.buildModel();
		Factory f = launcher.getFactory();

		final CtClass<?> ctClass = f.Class().get(testClass);

		//change the model
		transformation.accept(ctClass);

		//print the changed model
		launcher.prettyprint();

		//check the printed file
		resultChecker.accept(ctClass, getContentOfPrettyPrintedClassFromDisk(ctClass));
	}

	private static Launcher createLauncherWithSniperPrinter() {
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setPrettyPrinterCreator(() -> {
			return new SniperJavaPrettyPrinter(launcher.getEnvironment());
		});
		return launcher;
	}

	private String getContentOfPrettyPrintedClassFromDisk(CtType<?> type) {
		File outputFile = getFileForType(type);

		byte[] content = new byte[(int) outputFile.length()];
		try (InputStream is = new FileInputStream(outputFile)) {
			is.read(content);
		} catch (IOException e) {
			throw new RuntimeException("Reading of " + outputFile.getAbsolutePath() + " failed", e);
		}
		try {
			return new String(content, "ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	private File getFileForType(CtType<?> type) {
		File outputDir = type.getFactory().getEnvironment().getSourceOutputDirectory();
		return new File(outputDir, type.getQualifiedName().replace('.', '/') + ".java");
	}

	private static String getResourcePath(String className) {
		String r = "./src/test/java/" + className.replaceAll("\\.", "/") + ".java";
		if (new File(r).exists()) {
			return r;
		}
		r = "./src/test/resources/" + className.replaceAll("\\.", "/") + ".java";
		if (new File(r).exists()) {
			return r;
		}
		throw new RuntimeException("Resource of class " + className + " doesn't exist");
	}

	/**
	 * checks that printed code contains only expected changes
	 */
	private void assertIsPrintedWithExpectedChanges(CtType<?> ctClass, String printedSource, String... regExpReplacements) {
		assertEquals(0, regExpReplacements.length % 2);
		String originSource = ctClass.getPosition().getCompilationUnit().getOriginalSourceCode();
		//apply all expected replacements using Regular expressions
		int nrChanges = regExpReplacements.length / 2;
		for (int i = 0; i < nrChanges; i++) {
			String str = regExpReplacements[i];
			String replacement = regExpReplacements[i * 2 + 1];
			originSource = originSource.replaceAll(str, replacement);
		}
		//check that origin sources which expected changes are equal to printed sources
		assertEquals(originSource, printedSource);
	}


	private static String fileAsString(String path, Charset encoding)
			throws IOException	{
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}

	public void testToStringWithSniperPrinter(String inputSourcePath) throws Exception {

		final Launcher launcher = new Launcher();
		launcher.addInputResource(inputSourcePath);
		String originalContent = fileAsString(inputSourcePath, StandardCharsets.UTF_8).replace("\t", "");
		CtModel model = launcher.buildModel();

		new SourceFragmentCreator().attachTo(launcher.getFactory().getEnvironment());

		final SniperJavaPrettyPrinter sp = new SniperJavaPrettyPrinter(launcher.getEnvironment());

		launcher.getEnvironment().setPrettyPrinterCreator(
				() -> {
					return sp;
				}
		);
		List<CtElement> ops = model.getElements(new TypeFilter<>(CtElement.class));


		ops.stream()
				.filter(el -> !(el instanceof spoon.reflect.CtModelImpl.CtRootPackage)
				&& !(el instanceof spoon.reflect.factory.ModuleFactory.CtUnnamedModule)
				).forEach(el -> {
			try {
				sp.reset();
				sp.printElementSniper(el);
				//Contract, calling toString on unmodified AST elements should draw only from original.
				String result = sp.getResult();

				if (!SniperJavaPrettyPrinter.hasImplicitAncestor(el) && !(el instanceof CtPackage) && !(el instanceof CtReference)) {
					assertTrue(result.length() > 0);
				}

				assertTrue("ToString() on element (" + el.getClass().getName() + ") =  \"" + el + "\" is not in original content",
						originalContent.contains(result.replace("\t", "")));
			} catch (UnsupportedOperationException | SpoonException e) {
				//Printer should not throw exception on printable element. (Unless there is a bug in the printer...)
				fail("ToString() on Element (" + el.getClass().getName() + "): at " + el.getPath() + " lead to an exception: " + e);
			}
		});
	}

	@Test
	public void testToStringWithSniperOnElementScan() throws Exception {
		testToStringWithSniperPrinter("src/test/java/spoon/test/prettyprinter/testclasses/ElementScan.java");
	}

}
