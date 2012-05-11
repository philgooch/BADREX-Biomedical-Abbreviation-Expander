/*
 *  Copyright (c) 2012, Phil Gooch.
 *
 *  This software is licenced under the GNU Library General Public License,
 *  http://www.gnu.org/copyleft/gpl.html Version 3, 29 June 2007
 *
 *  Phil Gooch 04/2012
 *
 *  This software makes use (in simplified form) ideas developed in:
 *  A. S. Schwartz and M. A. Hearst, A Simple Algorithm for Identifying Abbreviation Definitions in Biomedical Text, in the Proceedings of the Pacific Symposium on Biocomputing, 8:451-462 (2003)
 *  H. Ao and T. Takagi, "ALICE: an algorithm to extract abbreviations from MEDLINE.", J Am Med Inform Assoc., 2005 Sep-Oct;12(5):576-86.
 */
package org.philgooch;

import gate.*;
import gate.creole.*;
import gate.creole.metadata.*;
import gate.util.*;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.event.ProgressListener;
import gate.creole.gazetteer.DefaultGazetteer;
import gate.event.StatusListener;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;
import java.io.*;
import java.net.*;

/**
 *
 * @author philipgooch
 */
@CreoleResource(name = "Biomedical Abbreviation Expander",
helpURL = "",
comment = "Uses regexes and lookup lists to expand biomedical abbreviations in text.")
public class BiomedicalAbbreviationExpander extends AbstractLanguageAnalyser implements ProgressListener,
        ProcessingResource,
        Serializable {

    // Init-time parameters
    private URL configFileURL;      // URL to configuration file that defines suffixes and key words
    private URL gazetteerListsURL;      // URL to gazetteer def file

    // Run-time parameters
    private String inputASName;     //  Input AnnotationSet name
    private String outputASName;    // Output AnnotationSet set name
    private String sentenceType;               // default to Sentence
    private String longType;        // annotation type to mark long form, or leave empty to inherit the type of any annotation that wraps the full text of the abbreviation found
    private String longTypeFeature;	// feature name to contain expanded abbreviation
    private String shortType;        // annotation type to mark short form, or leave empty to inherit the type of any annotation that wraps the full text of the abbreviation found
    private String shortTypeFeature;	// feature name to contain abbreviation
    private Boolean expandAllShortFormInstances;      // once a term-abbreviation pair has been identified, should all instances of that abbreviation be expanded?
    private Integer maxInner;               // maximum length of outer string (text before parens)
    private Integer maxOuter;               // maximum length of inner string (text inside parens)
    private Float threshold;                // fraction of abbrev chars that must match the term
    private Boolean swapShortest;           // swap inner with outer if outer is shorter than inner
    private Boolean useLookups;         // flag to determine whether to run gazetteer
    private Boolean useBidirectionMatch;      // flag to determine whether an additional pattern should be used for bidirectional matching
    private ArrayList<String> underlyingAnnots;     // list of annotations that may be contained in or contain the outer term that should be copied over to the short form

    // 
    private Map<String, Pattern> constraintsPatternMap;          // patterns that validate ALICE algorithm constraints
    private String posConstraints;      // regex fragment for truncating candidate terms after prepositions, determiners etc
    private String outer_pre;           // regex fragments for matching candidate term-abbrev pairs
    private String inner_pre;
    private String inner_post;
    private String outer_pre_2;
    private String inner_pre_2;
    private String inner_post_2;

    DefaultGazetteer gazetteer;         // gazetteer instance

    // Exit gracefully if exception caught on init()
    private boolean gracefulExit;

    /**
     * Pattern for matching text that contains one of a given list of words or regexes
     * @param key
     * @param options
     */
    private void addContainsPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            constraintsPatternMap.put(key, Pattern.compile("^.+\\b(" + option + ")\\b.+$"));
        }
    }

    /*
     * Pattern for matching text that starts with one of a given list of words or regexes
     * @param key
     * @param options
     */
    private void addStartsWithPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            constraintsPatternMap.put(key, Pattern.compile("^\\b(" + option + ")\\b.+$"));
        }
    }

    /**
     * Pattern for matching text that exactly matches one of a given list of words or regexes
     * @param key
     * @param options
     */
    private void addCompleteMatchPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            constraintsPatternMap.put(key, Pattern.compile("^(" + option + ")$"));
        }
    }

    @Override
    public Resource init() throws ResourceInstantiationException {
        gracefulExit = false;


        if (configFileURL == null) {
            gracefulExit = true;
            gate.util.Err.println("No configuration file provided!");
        }

        if (gazetteerListsURL == null) {
            gracefulExit = true;
            gate.util.Err.println("No gazetteer definition file provided!");
        }

        ConfigReader config = new ConfigReader(configFileURL);
        gracefulExit = config.config();

        try {
            HashMap<String, String> options = config.getOptions();

            constraintsPatternMap = new HashMap<String, Pattern>();
            addContainsPattern("to_be", options);
            addStartsWithPattern("prepositions", options);
            addStartsWithPattern("wh_adverbs", options);
            addCompleteMatchPattern("special", options);

            posConstraints = options.get("pos_constraints");
            outer_pre = options.get("outer_pre");
            inner_pre = options.get("inner_pre");
            inner_post = options.get("inner_post");
            outer_pre_2 = options.get("outer_pre_2");
            inner_pre_2 = options.get("inner_pre_2");
            inner_post_2 = options.get("inner_post_2");
        } catch (NullPointerException ne) {
            gracefulExit = true;
            gate.util.Err.println("Missing or unset configuration options. Please check configuration file.");
        }

        // Initialize Gazetteer
        initGaz();


        return this;
    } // end init()

    /**
     * 
     * @throws ResourceInstantiationException
     */
    private void initGaz() throws ResourceInstantiationException {
        FeatureMap params;
        FeatureMap features;

        params = Factory.newFeatureMap();
        params.put(DefaultGazetteer.DEF_GAZ_LISTS_URL_PARAMETER_NAME, gazetteerListsURL);
        params.put(DefaultGazetteer.DEF_GAZ_ENCODING_PARAMETER_NAME, "UTF-8");
        params.put(DefaultGazetteer.DEF_GAZ_FEATURE_SEPARATOR_PARAMETER_NAME, ";");

        if (gazetteer == null) {
            features = Factory.newFeatureMap();
            Gate.setHiddenAttribute(features, true);
            gazetteer = (DefaultGazetteer) Factory.createResource("gate.creole.gazetteer.DefaultGazetteer", params, features);
        } else {
            gazetteer.setParameterValues(params);
            gazetteer.reInit();
        }
    }

    /**
     * 
     * @param inputASName
     * @throws ExecutionException
     */
    private void runGaz(String inputASName) throws ExecutionException {
        FeatureMap params;
        params = Factory.newFeatureMap();
        params.put(DefaultGazetteer.DEF_GAZ_DOCUMENT_PARAMETER_NAME, document);
        params.put(DefaultGazetteer.DEF_GAZ_ANNOT_SET_PARAMETER_NAME, inputASName);
        params.put(DefaultGazetteer.DEF_GAZ_CASE_SENSITIVE_PARAMETER_NAME, "true");
        try {
            gazetteer.setParameterValues(params);
        } catch (ResourceInstantiationException re) {
            throw new ExecutionException(re);
        }

        ProgressListener pListener = null;
        StatusListener sListener = null;
        fireProgressChanged(5);
        if (isInterrupted()) {
            throw new ExecutionInterruptedException(
                    "The execution of the abbreviations expander has been abruptly interrupted!");
        }
        pListener = new IntervalProgressListener(5, 10);
        sListener = new StatusListener() {

            public void statusChanged(String text) {
                fireStatusChanged(text);
            }
        };
        gazetteer.addProgressListener(pListener);
        gazetteer.addStatusListener(sListener);
        gazetteer.execute();
        gazetteer.removeProgressListener(pListener);
        gazetteer.removeStatusListener(sListener);
    }

    @Override
    public void execute() throws ExecutionException {
        interrupted = false;

        // quit if setup failed
        if (gracefulExit) {
            gracefulExit("Plugin was not initialised correctly. Exiting gracefully ... ");
            return;
        }

        AnnotationSet inputAS = (inputASName == null || inputASName.trim().length() == 0) ? document.getAnnotations() : document.getAnnotations(inputASName);
        AnnotationSet outputAS = (outputASName == null || outputASName.trim().length() == 0) ? document.getAnnotations() : document.getAnnotations(outputASName);

        // Run the medical abbreviations gazetteer
        if (useLookups) {
            runGaz(inputASName);
        }

        if (maxOuter < 1) {
            maxOuter = 1;
        }
        if (maxInner < 1) {
            maxInner = 1;
        }
        int maxInnerChars = maxInner * 4;
        int maxOuterChars = maxOuter * 4;
        // Default maximum window of ten words in outer (text before parentheses) and 20 characters (approx 5 words) in inner (text inside parentheses)
        // Pattern matches a phrases where the first character in the outer matches the first abbrev character in the inner
        // Pattern abbrevExpansionPairPattern = Pattern.compile("\\b((\\w)\\W{0,2}(\\w+[\\&'/\\-\\+\\s]{1,2}){1," + maxOuter + "})\\s*[\\(\\[](\\2[\\w\\&'\\./\\-\\+\\s]{1," + maxInnerChars + "})([,;:]\\s*\\w+)?[\\)\\]]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern abbrevExpansionPairPattern = Pattern.compile(outer_pre + maxOuter + inner_pre + maxInnerChars + inner_post, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        // if we replace ['\\-\\+\\s]{1,2} with ['\\-\\+\\s]{0,2} we can allow for no space before the left parens but this has a big negative impact on performance

        // Pattern that matches phrases where the first character of the last word in the outer matches the last abbrev character in the inner
        // Pattern abbrevExpansionPairPattern2 = Pattern.compile("\\b(.{1," + maxOuterChars + "}\\b(\\w)(\\w+['/\\-\\+\\s]{1,2}))\\s*[\\(\\[](.{1," + maxInnerChars + "}\\2([,;:]\\s*\\w+)?)[\\)\\]]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern abbrevExpansionPairPattern2 = Pattern.compile(outer_pre_2 + maxOuterChars + inner_pre_2 + maxInnerChars + inner_post_2, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        // Map to hold abbreviations and their corresponding expansions
        Map<String, String> expansionMap = new HashMap<String, String>();
        // Map to hold sentences that have already been matched for the given abbreviation in the first matching phase
        Map<String, Annotation> alreadyMatchedMap = new HashMap<String, Annotation>();
        // Map to hold mappings between abbreviation and its underlying semantic type
        Map<String, String> abbrevTypeMap = new HashMap<String, String>();
        

        AnnotationSet sentenceAS = null;
        if (sentenceType != null && !sentenceType.isEmpty()) {
            sentenceAS = inputAS.get(sentenceType);
        }

        // Document content
        String docContent = document.getContent().toString();
        int docLen = docContent.length();

        // For matching purposes replace all whitespace characters with a single space
        docContent = docContent.replaceAll("[\\s\\xA0\\u2007\\u202F]", " ");

        fireStatusChanged("Locating abbreviations in " + document.getName());
        fireProgressChanged(0);

        if (sentenceAS != null) {
            List<Annotation> sentenceList = gate.Utils.inDocumentOrder(sentenceAS);
            for (Annotation sentence : sentenceList) {
                int sentStartOffset = sentence.getStartNode().getOffset().intValue();
                int sentEndOffset = sentence.getEndNode().getOffset().intValue();

                String sentenceContent = docContent.substring(sentStartOffset, sentEndOffset);

                int progress = 0;

                Matcher m1 = abbrevExpansionPairPattern.matcher(sentenceContent);
                // Extra matching pass - can lead to increased recall but reduced precision
                if (useBidirectionMatch) {
                    Matcher m2 = abbrevExpansionPairPattern2.matcher(sentenceContent);
                    boolean m1Found;
                    boolean m2Found;
                    int startPoint = 0;
                    do {
                        m1Found = false;
                        m2Found = false;
                        m1Found = m1.find(startPoint);
                        if (!m1Found) {
                            m2Found = m2.find(startPoint);
                        }
                        if (m1Found) {
                            doMatch(inputAS, outputAS, sentence, sentStartOffset, false, m1, constraintsPatternMap, expansionMap, alreadyMatchedMap, abbrevTypeMap, sentenceContent);
                            startPoint = m1.end();
                        }
                        if (m2Found) {
                            doMatch(inputAS, outputAS, sentence, sentStartOffset, true, m2, constraintsPatternMap, expansionMap, alreadyMatchedMap, abbrevTypeMap, sentenceContent);
                            startPoint = m2.end();
                        }

                        // Progress bar
                        progress++;
                        fireProgressChanged(progress / docLen);
                    } while (m1Found || m2Found);
                } else {
                    while (m1.find()) {
                        doMatch(inputAS, outputAS, sentence, sentStartOffset, false, m1, constraintsPatternMap, expansionMap, alreadyMatchedMap, abbrevTypeMap, sentenceContent);
                        // Progress bar
                        progress++;
                        fireProgressChanged(progress / docLen);
                    } // end while m1.find()
                } // end if

                // now find others instances of the abbreviations that we matched earlier
                if (expandAllShortFormInstances) {
                    for (Iterator<Map.Entry<String, String>> itr = expansionMap.entrySet().iterator(); itr.hasNext();) {
                        Map.Entry<String, String> entry = itr.next();
                        String abbrevKey = entry.getKey();
                        String termEntry = entry.getValue();
                        Annotation matchedSentence = alreadyMatchedMap.get(abbrevKey);
                        if (matchedSentence != null && !matchedSentence.equals(sentence)) {
                        	String abbrevNorm = getNormalizedAbbrev(abbrevKey);
                        	Pattern patt = Pattern.compile("\\b" + abbrevNorm + "\\b");
                            Matcher abbrevMatcher = patt.matcher(sentenceContent);
                            String underlyingShortType = abbrevTypeMap.get(abbrevKey);
                            if (underlyingShortType == null ) {
                                underlyingShortType = shortType;
                            }
                            while (abbrevMatcher.find()) {
                                int start = abbrevMatcher.start() + sentStartOffset;
                                int end = abbrevMatcher.end() + sentStartOffset;
                                addLookup(inputAS, outputAS, longTypeFeature, termEntry, underlyingShortType, start, end);
                            }
                        }
                    } // end for
                }
            } // end sentenceAS iterator

        } else {
            gracefulExit("No sentences to process!");
        }
        fireProcessFinished();
    } // end execute()


	/**
	* 
	* @param String input abbreviation string
	* @return regex pattern string normalized for white space. E.g. 2D 1H NMR -> 2D\s*1H\s*NMR
	*/
	private String getNormalizedAbbrev(String abbrev) {
		String[] abbrevArr = abbrev.split("\\s+");
		String abbrevNorm = "";
		int abbrevArrLen = abbrevArr.length;
		for (int i = 0; i < abbrevArrLen; i++) {
			abbrevNorm = abbrevNorm + Pattern.quote(abbrevArr[i]);
			if ( i < abbrevArrLen - 1) {
				abbrevNorm = abbrevNorm + "\\s*";
			}
		}
		return abbrevNorm;
	}
	
    /**
     *
     * @param inputAS
     * @param outputAS
     * @param sentence
     * @param sentStartOffset
     * @param secondPass
     * @param m1
     * @param constraintsPatternMap
     * @param expansionMap
     * @param alreadyMatchedMap
     * @param abbrevTypeMap
     * @param sentenceContent
     * @throws ExecutionInterruptedException
     */
    private void doMatch(AnnotationSet inputAS, AnnotationSet outputAS, Annotation sentence, int sentStartOffset, boolean secondPass, Matcher m1, Map<String, Pattern> patternMap, Map<String, String> expansionMap, Map<String, Annotation> alreadyMatchedMap, Map<String, String> abbrevTypeMap, String sentenceContent) throws ExecutionInterruptedException {
        int numMatches = 0;
        boolean isCandidateMatch = true;
        boolean swapped = false;        // flag to determine if abbrev and term have switched places
        if (isInterrupted()) {
            throw new ExecutionInterruptedException("Execution of Biomedical Abbreviation Expander was interrupted.");
        }

        String term = m1.group(1);
        String abbrev = m1.group(4);
        int termStart = m1.start(1);
        int termEnd = m1.end(1);
        int abbrevStart = m1.start(4);
        int abbrevEnd = m1.end(4);
        int abbrevLen = abbrev.length();
        int termLen = term.length();
        if (term.matches(".+\\s$")) {
            termEnd--;
            termLen--;
            term = term.substring(0, termLen);
        }
        if (abbrev.matches(".+\\s$")) {
            abbrevEnd--;
            abbrevLen--;
            abbrev = abbrev.substring(0, abbrevLen);
        }
        if (term.matches("^\\s.+$")) {
            term = term.substring(1, termLen);
            termStart++;
            termLen--;
        }
        if (abbrev.matches("^\\s.+$")) {
            abbrev = abbrev.substring(1, abbrevLen);
            abbrevStart++;
            abbrevLen--;
        }

        // Value judgement phase - check abbreviation does not meet discard conditions
        for (Iterator<Map.Entry<String, Pattern>> patternItr = patternMap.entrySet().iterator(); patternItr.hasNext();) {
            Map.Entry<String, Pattern> entry = patternItr.next();
            Pattern patt = entry.getValue();
            Matcher constraints = patt.matcher(abbrev);
            if (constraints.matches()) {
                isCandidateMatch = false;
            }
        }
        if (!isCandidateMatch) {
            return;
        }

        // Get first char of term, this will match the first char of the abbrev from our original regex
        // If the term begins or contains a preposition, check that the character following the preposition
        // matches our first char. If it does, truncate the term to this point
        // This corrects overlong spans such as 'a solution of sulfoquinovosyl diacylglycerol'
        String termFirstChar = term.substring(0, 1);
        String termLastChar = term.substring(termLen - 1, termLen);
        
        int upperBound = (maxOuter >= 2) ? maxOuter - 1 : maxOuter;

        Pattern containsPrep = Pattern.compile("^(" + termFirstChar + ".+\\b)?((" + posConstraints + ")\\s+){1," + upperBound + "}(\\b[^" + termFirstChar + "][^\\s]+\\s+){0,3}\\b" + termFirstChar, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern containsPrep2 = Pattern.compile("^.+" + termLastChar + "\\b((" + posConstraints + ")\\s+){1," + upperBound + "}\\b(.+" + termLastChar + "\\b)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher prepMatch;
        if (secondPass) {
            prepMatch = containsPrep2.matcher(term);
        } else {
            prepMatch = containsPrep.matcher(term);
        }

        if (prepMatch.find()) {
            int tmpTermStart;
            if (secondPass) {
                tmpTermStart = prepMatch.start(3);
            } else {
                tmpTermStart = prepMatch.end() - 1;
            }
            if (tmpTermStart > -1) {
                term = term.substring(tmpTermStart);
                termStart = termStart + tmpTermStart;
            }
        }

        // Remove non-alpha characters from term and abbrev
        String termClean = term.replaceAll("[\\d\\p{Punct}]", "").trim();
        String abbrevClean = abbrev.replaceAll("[\\d\\p{Punct}]", "").trim();

        // Tokenize the term - if it's more than one word and the last word is all caps, grab the last word
        String[] termTokens = termClean.split("\\s+");
        if (termTokens.length > 2) {
            String tmpTermClean = termTokens[termTokens.length - 1];
            String firstChar = term.substring(0, 1);
            String firstCharLastWord = tmpTermClean.substring(0, 1);
            if (tmpTermClean.matches("^[A-Z]{2,}$") && firstChar.equalsIgnoreCase(firstCharLastWord)) {
                termClean = termTokens[termTokens.length - 1];
                String[] termArr = term.split("\\s+");
                String tmpTerm = termArr[termArr.length - 1];
                termStart = termStart + term.lastIndexOf(tmpTerm);
                term = tmpTerm;
            }
        }

        // Tokenize the abbrev - if it's more than one word and the last word is all caps, grab the last word
        String[] abbrevTokens = abbrevClean.split("\\s+");
        if (abbrevTokens.length > 2) {
            String tmpAbbrevClean = abbrevTokens[abbrevTokens.length - 1];
            String firstChar = abbrev.substring(0, 1);
            String firstCharLastWord = tmpAbbrevClean.substring(0, 1);
            if (tmpAbbrevClean.matches("^[A-Z]{2,}$") && firstChar.equalsIgnoreCase(firstCharLastWord)) {
                abbrevClean = abbrevTokens[abbrevTokens.length - 1];
                String[] abbrevArr = abbrev.split("\\s+");
                String tmpAbbrev = abbrevArr[abbrevArr.length - 1];
                abbrevStart = abbrevStart + abbrev.lastIndexOf(tmpAbbrev);
                abbrev = tmpAbbrev;
            }
        }

        // check if we need to switch again
        abbrevLen = abbrev.length();
        termLen = term.length();

        if (abbrevLen >= termLen) {
            swapped = true;
            int tmpTermStart = termStart;
            int tmpTermEnd = termEnd;
            String tmpTerm = term;
            String tmpTermClean = termClean;
            termClean = abbrevClean;
            term = abbrev;
            abbrev = tmpTerm;
            abbrevClean = tmpTermClean;
            termStart = abbrevStart;
            termEnd = abbrevEnd;
            abbrevStart = tmpTermStart;
            abbrevEnd = tmpTermEnd;
        }

        termClean = termClean.toUpperCase();
        abbrevClean = abbrevClean.toUpperCase();

        int numTermTokens = termTokens.length;
        int numAbbrevChars = abbrevClean.length();

        int startPos = 0;
        for (int i = 0; i < numAbbrevChars; i++) {
            try {
                char abbrevChar = abbrevClean.charAt(i);
                Pattern p1 = Pattern.compile(Character.toString(abbrevChar));
                Matcher mm = p1.matcher(termClean);
                if (mm.find(startPos)) {
                    startPos = mm.end();
                    numMatches++;
                }
            } catch (IndexOutOfBoundsException se) {
                gate.util.Err.println("Unable to get char " + i + " from " + abbrevClean);
            }
        }

        // Have we matched the minimum number of abbrev chars?
        float thresh = (float) numMatches / (float) numAbbrevChars;
        if (thresh >= threshold) {
        	expansionMap.put(abbrev, term);
            alreadyMatchedMap.put(abbrev, sentence);

            // Copy over any existing semantic type that covers this term, rather than create a new annot
            String underlyingLongType = getUnderlyingAnnType(inputAS, termStart + sentStartOffset, termEnd + sentStartOffset);
            String underlyingShortType = shortType;
            if (underlyingLongType != null) {
                abbrevTypeMap.put(abbrev, underlyingLongType);
                underlyingShortType = underlyingLongType;
            }
            if (swapped && !swapShortest) {
                if (underlyingLongType == null) { underlyingLongType = shortType ; underlyingShortType = longType ;}
                addLookup(inputAS, outputAS, longTypeFeature, abbrev, underlyingLongType, termStart + sentStartOffset, termEnd + sentStartOffset);
                addLookup(inputAS, outputAS, shortTypeFeature, term, underlyingShortType, abbrevStart + sentStartOffset, abbrevEnd + sentStartOffset);
            } else {
                if (underlyingLongType == null) { underlyingLongType = longType ; }
                addLookup(inputAS, outputAS, shortTypeFeature, abbrev, underlyingLongType, termStart + sentStartOffset, termEnd + sentStartOffset);
                addLookup(inputAS, outputAS, longTypeFeature, term, underlyingShortType, abbrevStart + sentStartOffset, abbrevEnd + sentStartOffset);
            }

            if (expandAllShortFormInstances) {
                // now match any additional instances of this abbreviation in the same sentence
                String abbrevNorm = getNormalizedAbbrev(abbrev);
                Pattern patt = Pattern.compile("\\b" + abbrevNorm + "\\b");
                Matcher abbrevMatcher = patt.matcher(sentenceContent);
                int startFrom = abbrevEnd;
                while (abbrevMatcher.find(startFrom)) {
                    int start = abbrevMatcher.start();
                    int end = abbrevMatcher.end();
                    addLookup(inputAS, outputAS, longTypeFeature, term, underlyingShortType, start + sentStartOffset, end + sentStartOffset);
                    startFrom = end;
                }
            } // end if
        } // end if

    }

    /**
     *
     * @param inputAS           input annotation set
     * @param outputAS          output annotation set
     * @param featureName       output annotation feature name
     * @param featureValue      output annotation feature value
     * @param outputASType      output annotation type
     * @param start             start offset (int)
     * @param end               end offset (int)
     */
    private void addLookup(AnnotationSet inputAS, AnnotationSet outputAS, String featureName, String featureValue, String outputASType, int start, int end) {
        Long startOffset = new Long(start);
        Long endOffset = new Long(end);
        try {
            AnnotationSet currSectionAS = ((gate.annotation.AnnotationSetImpl) inputAS).getStrict(startOffset, endOffset).get(outputASType);
            if (currSectionAS.isEmpty()) {
                FeatureMap fm = Factory.newFeatureMap();
                fm.put(featureName, featureValue);
                outputAS.add(startOffset, endOffset, outputASType, fm);
            } else {
                Annotation curr = currSectionAS.iterator().next();
                FeatureMap fm = curr.getFeatures();
                fm.put(featureName, featureValue);
            }
        } catch (InvalidOffsetException ie) {
            // shouldn't happen
            gate.util.Err.println(ie);
        }
    }

    
    /**
     *
     * @param inputAS
     * @param outputAS
     * @param start
     * @param end
     * @return
     */
    private String getUnderlyingAnnType(AnnotationSet inputAS, int start, int end) {
        Long startOffset = new Long(start);
        Long endOffset = new Long(end);
        String underlyingAnnType = null;
        if (underlyingAnnots != null && !underlyingAnnots.isEmpty()) {
            for (String annType : underlyingAnnots) {
                AnnotationSet underlyingContainedAS = inputAS.getContained(startOffset, endOffset).get(annType);
                AnnotationSet underlyingContainingAS = inputAS.getCovering(annType, startOffset, endOffset);
                List<Annotation> underlyingAS = new ArrayList<Annotation>(underlyingContainedAS);
                underlyingAS.addAll(underlyingContainingAS);
                Collections.sort(underlyingAS, new OffsetComparator());
                // Just take the first one as we don't know which is the more significant if > 1
                if (!underlyingAS.isEmpty()) {
                    underlyingAnnType = underlyingAS.get(0).getType();
                }
            }
        }
        return underlyingAnnType;
    }

    
    /* Set gracefulExit flag and clean up */
    private void gracefulExit(String msg) {
        gate.util.Err.println(msg);
        cleanup();
        fireProcessFinished();
    }

    @Override
    public void cleanup() {
        Factory.deleteResource(gazetteer);
    }

    @Override
    public synchronized void interrupt() {
        super.interrupt();
        gazetteer.interrupt();
    }

    @Override
    public void progressChanged(int i) {
        fireProgressChanged(i);
    }

    @Override
    public void processFinished() {
        fireProcessFinished();
    }

    /* Setters and Getters
     * =======================
     */
    @Optional
    @RunTime
    @CreoleParameter(comment = "Input Annotation Set Name")
    public void setInputASName(String inputASName) {
        this.inputASName = inputASName;
    }

    public String getInputASName() {
        return inputASName;
    }

    @Optional
    @RunTime
    @CreoleParameter(comment = "Output Annotation Set Name")
    public void setOutputASName(String outputASName) {
        this.outputASName = outputASName;
    }

    public String getOutputASName() {
        return outputASName;
    }

    @RunTime
    @CreoleParameter(defaultValue = "Term", comment = "Output Annotation name for long form mentions.")
    public void setLongType(String longType) {
        this.longType = longType;
    }

    public String getLongType() {
        return longType;
    }

    @RunTime
    @CreoleParameter(defaultValue = "Abbrev", comment = "Output Annotation name for short form mentions.")
    public void setShortType(String shortType) {
        this.shortType = shortType;
    }

    public String getShortType() {
        return shortType;
    }

    @RunTime
    @CreoleParameter(defaultValue = "longForm", comment = "Output Annotation feature for abbreviation expansion.")
    public void setLongTypeFeature(String longTypeFeature) {
        this.longTypeFeature = longTypeFeature;
    }

    public String getLongTypeFeature() {
        return longTypeFeature;
    }

    @RunTime
    @CreoleParameter(defaultValue = "shortForm", comment = "Output Annotation feature for abbreviation.")
    public void setShortTypeFeature(String shortTypeFeature) {
        this.shortTypeFeature = shortTypeFeature;
    }

    public String getShortTypeFeature() {
        return shortTypeFeature;
    }

    public URL getConfigFileURL() {
        return configFileURL;
    }

    @CreoleParameter(defaultValue = "resources/config.txt",
    comment = "Location of configuration file")
    public void setConfigFileURL(URL configFileURL) {
        this.configFileURL = configFileURL;
    }

    @CreoleParameter(defaultValue = "resources/lookup/abbrevs.def",
    comment = "Location of gazetteer definition file")
    public void setGazetteerListsURL(URL gazetteerListsURL) {
        this.gazetteerListsURL = gazetteerListsURL;
    }

    public URL getGazetteerListsURL() {
        return gazetteerListsURL;
    }

    @Optional
    @RunTime
    @CreoleParameter(defaultValue = ANNIEConstants.SENTENCE_ANNOTATION_TYPE,
    comment = "Sentence annotation name")
    public void setSentenceType(String sentenceName) {
        this.sentenceType = sentenceName;
    }

    public String getSentenceType() {
        return sentenceType;
    }

    @RunTime
    @CreoleParameter(defaultValue = "false",
    comment = "Should all short-form abbreviations be expanded once located?")
    public void setExpandAllShortFormInstances(Boolean expandAllShortFormInstances) {
        this.expandAllShortFormInstances = expandAllShortFormInstances;
    }

    public Boolean getExpandAllShortFormInstances() {
        return expandAllShortFormInstances;
    }

    @RunTime
    @CreoleParameter(defaultValue = "10",
    comment = "Maximum number of words for candidate abbreviation")
    public void setMaxInner(Integer maxInner) {
        this.maxInner = maxInner;
    }

    public Integer getMaxInner() {
        return maxInner;
    }

    @RunTime
    @CreoleParameter(defaultValue = "10",
    comment = "Maximum number of words for candidate term")
    public void setMaxOuter(Integer maxOuter) {
        this.maxOuter = maxOuter;
    }

    public Integer getMaxOuter() {
        return maxOuter;
    }

    @RunTime
    @CreoleParameter(defaultValue = "0.80",
    comment = "Ratio of characters in candidate abbreviation that must match the candidate term")
    public void setThreshold(Float threshold) {
        this.threshold = threshold;
    }

    public Float getThreshold() {
        return threshold;
    }

    @RunTime
    @CreoleParameter(defaultValue = "true",
    comment = "Swap inner with outer if outer is shorter than inner")
    public void setSwapShortest(Boolean swapShortest) {
        this.swapShortest = swapShortest;
    }

    public Boolean getSwapShortest() {
        return swapShortest;
    }

    @RunTime
    @CreoleParameter(defaultValue = "false",
    comment = "Run lookups gazetteer of common medical abbreviations?")
    public void setUseLookups(Boolean useLookups) {
        this.useLookups = useLookups;
    }

    public Boolean getUseLookups() {
        return useLookups;
    }

    @RunTime
    @CreoleParameter(defaultValue = "false",
    comment = "Makes use of an additional regex pattern for bidirectional matching")
    public void setUseBidirectionMatch(Boolean useBidirectionMatch) {
        this.useBidirectionMatch = useBidirectionMatch;
    }

    public Boolean getUseBidirectionMatch() {
        return useBidirectionMatch;
    }

    @Optional
    @RunTime
    @CreoleParameter(defaultValue = "AnatomicalSite;DiseaseOrSyndrome;Procedure;Test;Cell;Protein;Chemical", comment = "List of annotations that, if they contain or are contained in the outer, should be copied to the inner")
    public void setUnderlyingAnnots(ArrayList<String> underlyingAnnots) {
        this.underlyingAnnots = underlyingAnnots;
    }

    public ArrayList<String> getUnderlyingAnnots() {
        return underlyingAnnots;
    }
}
