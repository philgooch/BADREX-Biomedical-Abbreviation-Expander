/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.philgooch;

import java.net.URL;
import java.util.HashMap;
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
public class ConfigReaderTest {

    public ConfigReaderTest() {
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
     * Test of getConfigURL method, of class ConfigParser.
     */
    @Test
    public void testGetConfigURL() {
        System.out.println("getConfigURL");
        ConfigReader instance = new ConfigReader();

        URL expResult = getClass().getResource("../../resources/config.txt");

        instance.setConfigURL(expResult);
        URL result = instance.getConfigURL();

        assertNotNull(expResult);
        assertNotNull(result);
        assertEquals(expResult, result);

    }

    

    /**
     * Test of populateOptions method, of class ConfigParser.
     */
    @Test
    public void testPopulateOptions() {
        System.out.println("populateOptions");
        ConfigReader instance = new ConfigReader();

        URL url = getClass().getResource("../../resources/config.txt");
        instance.setConfigURL(url);

        assertNotNull(instance.getConfigURL());

        boolean expResult = false;
        boolean result = instance.config();
        assertEquals(expResult, result);
        
    }

}