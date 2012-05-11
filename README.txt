Copyright (c) 2012, Phil Gooch. 
This software is licenced under the GNU Library General Public License,
http://www.gnu.org/copyleft/gpl.html Version 3, 29 June 2007

See LICENSE.txt file for license details.


BADREX: Biomedical Abbreviation Expander
========================================

BADREX identifies term-abbreviation pairs using dynamic regular expressions that generalise and extend the Schwartz-Hearst algorithm[1]. In addition it uses a subset of the inner-outer selection rules described in Ao & Takagi's ALICE algorithm[2]. Rather than simply extracting terms and their abbreviations, it annotates them in situ and adds the corresponding long form and short form text as features on each.

It also has the option of expanding all abbreviations in the text that match the short-form of the most recently matched long-form--short-form pair. In addition, there is the option of annotating and classifying common medical abbreviations extracted from Wikipedia[3]. 

Against the Medstract corpus (http://www.medstract.org/) it achieves precision and recall of 98% and 97% respectively.

The gold standard markables used for evaluation can be downloaded from http://soi.city.ac.uk/~abdy181/software/GATE/BADREX/yeast_abbrev_labeled.xml and http://soi.city.ac.uk/~abdy181/software/GATE/BADREX/medstract_corrected_pairs.txt


[1] A. S. Schwartz and M. A. Hearst, A Simple Algorithm for Identifying Abbreviation Definitions in Biomedical Text, in the Proceedings of the Pacific Symposium on Biocomputing, 8:451-462 (2003).
[2] Ao H, Takagi T. ALICE: an algorithm to extract abbreviations from MEDLINE. J Am Med Inform Assoc. 2005 Sep-Oct;12(5):576-86. 
[3] http://en.wikipedia.org/wiki/List_of_medical_abbreviations


How to use BADREX
=================

BADREX is compatible with GATE version 6.1 and higher. The plugin can be loaded via the GATE Java API, or in GATE Developer go to File->Manage Creole Plugins, click the 'Add new CREOLE repository' button and select the 'BiomedicalAbbreviationExpander' directory.

Be sure to add a RegEx Sentence Splitter to your pipeline before running this plugin. (The ANNIE Sentence Splitter can also be used, although this also requires a Tokenizer.)

To favour precision over recall, set maxInner and maxOuter to low values, e.g. 5, and set the threshold to 1.0 or 0.9
To favour recall over precision, set maxInner and maxOuter to high values, e.g. 10, and set the threshold to 0.75 or below


Parameters
==========

Init-time
----------
configFileURL:	Location of configuration file that lists the stop-words and lookup files
gazetteerListsURL: Location of gazetteer definition file for lists of common medical abbreviations


Run-time
---------
inputASName:		Input annotation set name
outputASName:		Output annotation set name
sentenceType:		Annotation type for Sentence annotations. Defaults to Sentence.
longType:		Annotation type to mark the term's long form
longTypeFeature:	Feature name to contain the expanded abbreviation on the short form annotation
shortType:		Annotation type to mark the term's short form
shortTypeFeature:	Feature name to contain the abbreviation on the long form annotation
expandAllShortFormInstances:      Once a term-abbreviation pair has been identified, should all instances of that abbreviation be annotated and expanded? Defaults to false.
maxInner:               Maximum length of outer string (text before parentheses)
maxOuter:               Maximum length of inner string (text inside parentheses)
threshold:              Fraction of short form characters that must match the long form to count as a match
swapShortest:           Swap annotation types if the outer phrase is shorter than the inner phrase? Defaults to true (some datasets always annotate the outer phrase the same way, even if the inner phrase is the abbreviation) 
useLookups:		Set to true to run a gazetteer lookup of common medical abbreviations
useBidirectionMatch:	In the event of the first character of the inner not matching the first character of the outer, attempts a match against the first character of the last word of the outer against the last character in the outer. Defaults to false. Can boost recall but reduce precision if set to true.
underlyingAnnots:	Use these annotations to tag the term and abbreviation if they contain or are contained in the matched long-form of the term. Clear this list to use the default longType and shortType annotations.