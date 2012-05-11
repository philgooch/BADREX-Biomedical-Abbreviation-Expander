/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.philgooch;

import gate.Resource;
import java.net.URL;
import java.util.ArrayList;

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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author philipgooch
 */
public class BiomedicalAbbreviationExpanderTest {

    public BiomedicalAbbreviationExpanderTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    
    /**
     * Test of init method, of class BiomedicalAbbreviationExpander.
     */
    @Test
    public void testInit() throws Exception {
        System.out.println("init");
        Gate.init();
        
        BiomedicalAbbreviationExpander badrex = new BiomedicalAbbreviationExpander();
        URL configUrl = getClass().getResource("../../resources/config.txt");
        URL gazUrl = getClass().getResource("../../resources/lookup/abbrevs.def");
        badrex.setConfigFileURL(configUrl);
        badrex.setGazetteerListsURL(gazUrl);
        badrex.setExpandAllShortFormInstances(Boolean.FALSE);
        badrex.setLongType("Long");
        badrex.setLongTypeFeature("longForm");
        badrex.setMaxInner(10);
        badrex.setMaxOuter(10);
        badrex.setSentenceType("Sentence");
        badrex.setShortType("Short");
        badrex.setShortTypeFeature("shortForm");
        badrex.setSwapShortest(Boolean.TRUE);
        badrex.setThreshold(0.9f);
        badrex.setUseBidirectionMatch(Boolean.FALSE);
        badrex.setUseLookups(Boolean.FALSE);
        
        Resource result = badrex.init();
        
        // Create a stub Gate app and document, sentence splitter, and BADREX
        Document d = Factory.newDocument("Wiskott-Aldrich syndrome (WAS) is an X-linked recessesive disorder.");
        Corpus corpus = Factory.newCorpus("test corpus");
        File pluginsDir = Gate.getPluginsHome();
        //  load the Tools plugin
        File aPluginDir = new File(pluginsDir, "ANNIE");
        // load the plugin.
        Gate.getCreoleRegister().registerDirectories(aPluginDir.toURI().toURL());

        LanguageAnalyser sentenceSplitter = (LanguageAnalyser)Factory.createResource("gate.creole.splitter.RegexSentenceSplitter");
        SerialAnalyserController serialController = (SerialAnalyserController) Factory.createResource("gate.creole.SerialAnalyserController");
        serialController.add(sentenceSplitter);
       
        corpus.add(d);
        serialController.setCorpus(corpus);
        serialController.execute();
        corpus.clear();

        badrex.setDocument(d);
        badrex.execute();

        assertNotNull(result);

        AnnotationSet defaultAS = d.getAnnotations();
        AnnotationSet abbrevAS = d.getAnnotations().get("Short");
        AnnotationSet termAS = d.getAnnotations().get("Long");
        Annotation term = termAS.iterator().next();
        Annotation abbrev = abbrevAS.iterator().next();
        FeatureMap termFeats = term.getFeatures();
        FeatureMap abbrevFeats = abbrev.getFeatures();
        String shortForm = (String)termFeats.get("shortForm");
        String longForm = (String)abbrevFeats.get("longForm");
        
        assertFalse(defaultAS.isEmpty());
        assertFalse(abbrevAS.isEmpty());
        assertFalse(termAS.isEmpty());
        assertNotNull(shortForm);
        assertNotNull(longForm);
        assertEquals(shortForm, "WAS");
        assertEquals(longForm, "Wiskott-Aldrich syndrome");
        Factory.deleteResource(d);
   
    }

}