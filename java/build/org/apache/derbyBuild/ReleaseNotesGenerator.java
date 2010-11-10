/*

   Derby - Class org.apache.derbyBuild.ReleaseNotesGenerator

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derbyBuild;

import java.io.*;
import java.util.*;
import org.w3c.dom.*;
import java.net.URL;
import org.apache.tools.ant.BuildException;

/**
 * <p>
 * This tool generates Release Notes for a Derby release. See the USAGE
 * constant for details on how to run this tool standalone. It is recommended
 * that you freshly regenerate your BUG_LIST just before you run this tool.
 * </p>
 *
 * <p>
 * The tool is designed to be run from Derby's ant build scripts. The build
 * script will integrate the various steps of generating the release notes into
 * a single ant target. This includes generating the issue list by querying
 * the Apache JIRA instance. For this reason, the properties below must be
 * specified when invoking the ant target. You can specify them in
 * <tt>ant.properties</tt>, or on the command line.<br/>
 * To run under ant, do the following:
 * </p>
 *
 * <ul>
 *      <li>Make sure the Maven 2 executable is in your path.</li>
 *      <li>Fill in information in <tt>releaseSummary.xml</tt>.<br/>
 *          See <tt>tools/release/templates/releaseSummaryTemplate.xml</tt>
 *          for details.</li>
 *      <li>Define <tt>jira.user</tt>.<br/>
 *          This variable is your JIRA user name.</li>
 *      <li>Define <tt>jira.password</tt>.<br/>
 *          This variable is your JIRA password.</li>
 *      <li>Define <tt>jira.filter.id</tt>.<br/>
 *          This variable holds the id for the manually created JIRA filter
 *          that will select the issues addressed by the release. The id
 *          consists of digits only.</li>
 *      <li>Define <tt>release.version</tt>.<br/>
 *          The version of the release, i.e. "10.7.1.0".</li>
 *      <li>Define <tt>relnotes.src.reports</tt>.<br/>
 *          This variable points at the directory which holds the list of JIRA
 *          issues addressed by the release. The file, called
 *          <tt>fixedBugsList.txt</tt>, will be generated when you invoke the
 *          ant target.</li>
 *      <li>cd into <tt>tools/release</tt> and run ant thusly:
 *          <tt>ant [properties] genrelnotes</tt></li>
 * </ul>
 * Running the ant target successfully requires a working Internet connection
 * to the Apache JIRA instance, as well as a valid JIRA username/password and
 * the id of an existing JIRA filter.
 * </p>
 * <p>For more information on this tool, please see the JIRAs which introduced it:
 * <ul> <li><a href="http://issues.apache.org/jira/browse/DERBY-4857">DERBY-4857</a></li>
 *      <li><a href="http://issues.apache.org/jira/browse/DERBY-2570">DERBY-2570</a></li>
 * </ul>
 * </p>
 */
public class ReleaseNotesGenerator extends GeneratorBase {
    /////////////////////////////////////////////////////////////////////////
    //
    //  CONSTANTS
    //
    /////////////////////////////////////////////////////////////////////////

    private static  final   String  USAGE =
        "Usage:\n" +
        "\n" +
        "  java org.apache.derbyBuild.ReleaseNotesGenerator SUMMARY BUG_LIST OUTPUT_PAMPHLET\n" +
        "\n" +
        "    where\n" +
        "                  SUMMARY                    Summary, a filled-in copy of releaseSummaryTemplate.xml.\n" +
        "                  BUG_LIST                   A report of issues addressed by this release, generated by the Derby JIRA SOAP client.\n" +
        "                  OUTPUT_PAMPHLET  The output file to generate, typically RELEASE-NOTES.html.\n" +
        "\n" +
        "The ReleaseNoteGenerator attempts to connect to issues.apache.org in\n" +
        "order to read the detailed release notes that have been clipped to\n" +
        "individual JIRAs. Before running this program, make sure that you can\n" +
        "ping issues.apache.org.\n" +
        "\n" +
        "The ReleaseNoteGenerator assumes that the JIRA report contains\n" +
        "key, title, fix versions and attachment id elements for each Derby\n" +
        "issue. For each issue in with an attachment id element the\n" +
        "ReleaseNotesGenerator grabs the (latest) releaseNote.html file.\n" +
        "\n" +
        "For this reason, it is recommended that you freshly generate BUG_LIST\n" +
        "just before you run this tool.\n"
        ;


    // major sections
    private static  final   String  OVERVIEW_SECTION = "Overview";
    private static  final   String  NEW_FEATURES_SECTION = "New Features";
    private static  final   String  BUG_FIXES_SECTION = "Bug Fixes";
    private static  final   String  ISSUES_SECTION = "Issues";
    private static  final   String  BUILD_ENVIRONMENT_SECTION = "Build Environment";
    private static  final   String  RELEASE_VERIFICATION_SECTION = "Verifying Releases";

    // headlines
    private static  final   String  ANT_HEADLINE = "Ant";
    private static  final   String  BRANCH_HEADLINE = "Branch";
    private static  final   String  COMPILER_HEADLINE = "Compiler";
    private static  final   String  JAVA6_HEADLINE = "Java 6";
    private static  final   String  JDK14_HEADLINE = "JDK 1.4";
    private static  final   String  JSR169_HEADLINE = "JSR 169";
    private static  final   String  MACHINE_HEADLINE = "Machine";
    private static  final   String  OSGI_HEADLINE = "OSGi";

    // tags in summary xml
    private static  final   String  SUM_ANT_VERSION = "antVersion";
    private static  final   String  SUM_COMPILER = "compilers";
    private static  final   String  SUM_JAVA6 = "java6";
    private static  final   String  SUM_JDK14 = "jdk1.4";
    private static  final   String  SUM_JSR169 = "jsr169";
    private static  final   String  SUM_MACHINE = "machine";
    private static  final   String  SUM_NEW_FEATURES = "newFeatures";
    private static  final   String  SUM_OSGI = "osgi";
    private static  final   String  SUM_OVERVIEW = "overview";
    private static  final   String  SUM_RELEASE_VERIFICATION = "releaseVerification";

    /////////////////////////////////////////////////////////////////////////
    //
    //  STATE
    //
    /////////////////////////////////////////////////////////////////////////

    private ReleaseNoteReader releaseNoteReader = new ReleaseNoteReader(docBldr);
    // set on the command line or by ant

    private List<JiraIssue> missingReleaseNotes = new ArrayList<JiraIssue>();


    public ReleaseNotesGenerator() throws Exception {
    }

    /**
     * Generate the release notes (for details on how to invoke this tool, see
     * the header comment on this class).
     * @param args command line arguments
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        ReleaseNotesGenerator   me = new ReleaseNotesGenerator();

        me._invokedByAnt = false;

        if (me.parseArgs(args)) { me.execute(); }
        else { me.println(USAGE); }
    }

    /////////////////////////////////////////////////////////////////////////
    //
    //  ANT Task BEHAVIOR
    //
    /////////////////////////////////////////////////////////////////////////

    /**
     * This is Ant's entry point into this task.
     * @throws BuildException
     */
    public void execute() throws BuildException {
        try {
            beginOutput();
            buildOverview();
            buildNewFeatures();
            parseBugsList();
            buildFixedBugsList();
            buildReleaseNoteIssuesList();
            buildEnvironment();
            buildReleaseVerification();
            replaceVariables();
            printOutput();

            printMissingReleaseNotes();
            printErrors();
        }
        catch (Throwable t) {
            t.printStackTrace();

            throw new BuildException("Error running ReleaseNotesGenerator: " +
                    t.getMessage(), t);
        }
    }


    /////////////////////////////////////////////////////////////////////////
    //
    //  MINIONS
    //
    /////////////////////////////////////////////////////////////////////////

    /**
     * Start the RELEASE_NOTES html docment.
     */
    private void beginOutput() throws Exception {
        String titleText = "Release Notes for Derby " + releaseID;
        Element html = outputDoc.createElement(HTML);
        Element title = createTextElement(outputDoc, "title", titleText);
        Element body = outputDoc.createElement(BODY);

        outputDoc.appendChild(html);
        html.appendChild(title);
        html.appendChild(body);

        Element bannerBlock = createHeader(body, BANNER_LEVEL, titleText);
        buildDelta(bannerBlock);

        Element toc = createList(body);

        createSection(body, MAIN_SECTION_LEVEL, toc,
                OVERVIEW_SECTION, OVERVIEW_SECTION);
        createSection(body, MAIN_SECTION_LEVEL, toc,
                NEW_FEATURES_SECTION, NEW_FEATURES_SECTION);
        createSection(body, MAIN_SECTION_LEVEL, toc,
                BUG_FIXES_SECTION, BUG_FIXES_SECTION);
        createSection(body, MAIN_SECTION_LEVEL, toc,
                ISSUES_SECTION, ISSUES_SECTION);
        createSection(body, MAIN_SECTION_LEVEL, toc,
                BUILD_ENVIRONMENT_SECTION, BUILD_ENVIRONMENT_SECTION);
        createSection(body, MAIN_SECTION_LEVEL, toc,
                RELEASE_VERIFICATION_SECTION, RELEASE_VERIFICATION_SECTION);
    }


    //////////////////////////////////
    //
    //  Overview SECTION
    //
    //////////////////////////////////

    /**
     * Build the Overview section.
     */
    private void buildOverview() throws Exception {
        // copy the details out of the summary file into the overview section
        cloneChildren(summary.getElementByTagName(SUM_OVERVIEW),
                getSection(outputDoc, MAIN_SECTION_LEVEL, OVERVIEW_SECTION));
    }

    //////////////////////////////////
    //
    //  New Features SECTION
    //
    //////////////////////////////////

    /**
     * Build the New Features section.
     */
    private void buildNewFeatures() throws Exception {
        // copy the details out of the summary file into the overview section
        cloneChildren(summary.getElementByTagName(SUM_NEW_FEATURES),
                getSection(outputDoc, MAIN_SECTION_LEVEL,
                NEW_FEATURES_SECTION));
    }

    //////////////////////////////////
    //
    //  Bug List SECTION
    //
    //////////////////////////////////

    private void parseBugsList()
            throws Exception {
        bugList = JiraIssue.createJiraIssueList(bugListFileName);
        // Parse the file for the release version and the previous version to
        // carry out sanity checks.
        BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(bugListFileName), "UTF-8"));
        String line;
        String prevVer = null;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("// Previous release:")) {
                prevVer = line.split(":")[1].trim();
                break;
            }
        }
        in.close();
        if (prevVer == null) {
            System.out.println(
                    "WARNING: Skipped previous release version sanity check.");
        } else if (!prevVer.equals(previousReleaseID)) {
            throw new IllegalStateException("previous release version " +
                    "mismatch between releaseSummary.xml and bug list: " +
                    previousReleaseID + " != " + prevVer);
        }
    }

    /**
     * Build the Bug List section.
     * @param gs state
     */
    private void buildFixedBugsList()
        throws Exception
    {
        Element bugListSection = getSection(outputDoc, MAIN_SECTION_LEVEL,
                BUG_FIXES_SECTION );

        String deltaStatement =
            "The following issues are addressed by Derby release " + releaseID +
            ". These issues are not addressed in the preceding " +
            previousReleaseID + " release.";

        addParagraph(bugListSection, deltaStatement);

        Element table = createTable
            (bugListSection, DEFAULT_TABLE_BORDER_WIDTH,
            new String[] { ISSUE_ID_HEADLINE, DESCRIPTION_HEADLINE });

        for ( Iterator i=bugList.iterator(); i.hasNext(); ) {
            JiraIssue issue = (JiraIssue) i.next();
            //println("Fixed: "+ issue.getKey());
            Element row = insertRow(table);
            Element linkColumn = insertColumn(row);
            Element descriptionColumn = insertColumn(row);
            Element hotlink = createLink(outputDoc, issue.getJiraAddress(),
                    "DERBY-" + issue.getKey());
            Text title = outputDoc.createTextNode(issue.getTitle());

            linkColumn.appendChild(hotlink);
            descriptionColumn.appendChild(title);
        }
    }

    //////////////////////////////////
    //
    //  Issues SECTION
    //
    //////////////////////////////////

    /**
     * Build the Issues section.
     */
    private void buildReleaseNoteIssuesList()
        throws Exception {
        Element issuesSection = getSection(outputDoc, MAIN_SECTION_LEVEL,
                ISSUES_SECTION);
        String deltaStatement =
            "Compared with the previous release (" + previousReleaseID +
            "), Derby release " + releaseID + " introduces the following " +
            "new features " +
            "and incompatibilities. These merit your special attention.";

        addParagraph(issuesSection, deltaStatement);
        Element toc = createList(issuesSection);

        for (Iterator i=bugList.iterator(); i.hasNext(); ) {
            JiraIssue issue = (JiraIssue) i.next();
            if (issue.hasReleaseNote()) {
                Node summaryText = null;
                Element details = null;
                try {
                    URL url = new URL(issue.getReleaseNoteAddress());
                    InputStream is = url.openStream();
                    Document releaseNote = releaseNoteReader.getReleaseNote(is);
                    summaryText = releaseNoteReader.
                            getReleaseNoteSummary(releaseNote);
                    details = releaseNoteReader.
                            getReleaseNoteDetails(releaseNote);
                } catch (Throwable t) {
                    errors.add(formatError("Unable to read or parse " +
                            "release note for DERBY-" +
                            issue.getKey(), t));
                    missingReleaseNotes.add(issue);
                    continue;
                }

                String key = "Note for DERBY-" + issue.getKey();
                //println("Release note: "+issue.getKey()+" - "+issue.getTitle());
                Element paragraph = outputDoc.createElement(PARAGRAPH);
                paragraph.appendChild(outputDoc.createTextNode(key + ": "));
                cloneChildren(summaryText, paragraph);
                insertLine(issuesSection);
                Element issueSection = createSection(issuesSection,
                        ISSUE_DETAIL_LEVEL, toc, key, paragraph);
                cloneChildren(details, issueSection);
            } else if (issue.hasMissingReleaseNote()) {
                missingReleaseNotes.add(issue);
            }
        }
    }

    //////////////////////////////////
    //
    //  Build Environment SECTION
    //
    //////////////////////////////////

    /**
     * Build the section describing the build environment.
     */
    private void buildEnvironment()
        throws Exception {
        Element environmentSection = getSection(outputDoc, MAIN_SECTION_LEVEL,
                BUILD_ENVIRONMENT_SECTION );

        String desc = "Derby release " + releaseID +
                " was built using the following environment:";

        addParagraph(environmentSection, desc);
        Element list = createList(environmentSection);

        addHeadlinedItem(list, BRANCH_HEADLINE,
                "Source code came from the " + branch + " branch.");

        addHeadlinedItem(list, MACHINE_HEADLINE,
                summary.getTextByTagName(SUM_MACHINE));

        addHeadlinedItem(list, ANT_HEADLINE,
                summary.getTextByTagName(SUM_ANT_VERSION));

        addHeadlinedItem(list, JDK14_HEADLINE,
                summary.getTextByTagName(SUM_JDK14));

        addHeadlinedItem(list, JAVA6_HEADLINE,
                summary.getTextByTagName(SUM_JAVA6));

        addHeadlinedItem(list, COMPILER_HEADLINE,
                summary.getTextByTagName(SUM_COMPILER));

        addHeadlinedItem(list, JSR169_HEADLINE,
                summary.getTextByTagName(SUM_JSR169));
    }

    //////////////////////////////////
    //
    //  Release Verification SECTION
    //
    //////////////////////////////////

    /**
     * Build the Release Verification section.
     */
    private void buildReleaseVerification() throws Exception {
        // copy the details out of the summary file into the release verification section
        cloneChildren(summary.getElementByTagName(SUM_RELEASE_VERIFICATION),
                getSection(outputDoc, MAIN_SECTION_LEVEL, RELEASE_VERIFICATION_SECTION));
    }


    //////////////////////////////////
    //
    //  Print errors
    //
    //////////////////////////////////

    /**
     * Print missing release notes
     */
    private void printMissingReleaseNotes() throws Exception {
        if (missingReleaseNotes.isEmpty()) {
            return;
        }
        println("The following JIRA issues still need release notes or the " +
                "release notes provided are unreadable:");

        for (Iterator i = missingReleaseNotes.iterator(); i.hasNext();) {
            JiraIssue issue = (JiraIssue) i.next();
            println("\t" + issue.getKey() + "\t" + issue.getTitle());
        }
    }


    //////////////////////////////////
    //
    //  ARGUMENT MINIONS
    //
    //////////////////////////////////

    /**
     * Returns true if arguments parse successfully, false otherwise.
     */
    private boolean    parseArgs( String[] args )
        throws Exception
    {
        if ( (args == null) || (args.length != 4) ) { return false; }

        int     idx = 0;

        setSummaryFileName( args[ idx++ ] );
        setBugListFileName( args[ idx++ ] );
        setOutputFileName( args[ idx++ ] );

        return true;
    }
}
