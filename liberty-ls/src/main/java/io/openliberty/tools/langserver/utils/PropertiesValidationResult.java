/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation
*******************************************************************************/
package io.openliberty.tools.langserver.utils;

import java.math.BigInteger;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.Range;

import io.openliberty.tools.langserver.ls.LibertyTextDocument;
import io.openliberty.tools.langserver.model.propertiesfile.PropertiesEntryInstance;

public class PropertiesValidationResult {
    Integer lineNumber;
    boolean hasErrors;
    String diagnosticType;
    PropertiesEntryInstance entry;
    LibertyTextDocument textDocumentItem;
    
    private static final Logger LOGGER = Logger.getLogger(PropertiesValidationResult.class.getName());

    public PropertiesValidationResult(PropertiesEntryInstance entry) {
        this.entry = entry;
        this.textDocumentItem = entry.getTextDocument();
        this.hasErrors = false;
    }

    /**
     * Validates if line has valid value for the provided key. Returns a PropertiesValidationResult object containing validation information.
     * @param line
     * @param textDocumentItem
     * @return PropertiesValidationResult with information
     */
    public static PropertiesValidationResult validateServerProperty(String line, LibertyTextDocument textDocumentItem) {
        PropertiesEntryInstance entry = new PropertiesEntryInstance(line, textDocumentItem);
        PropertiesValidationResult result = new PropertiesValidationResult(entry);
        result.validateServerProperty();
        return result;
    }

    /**
     * Raises hasErrors flag if invalid value is detected.
     */
    // Note: If setting hasErrors to true, MUST set diagnosticType as well for diagnostic message retrieval.
    public void validateServerProperty() {
        if (entry.isComment()) {
            return;
        }
        String property = entry.getKey();
        String value = entry.getValue();

        if (ServerPropertyValues.usesPredefinedValues(textDocumentItem, property)) {
            List<String> validValues = ServerPropertyValues.getValidValues(textDocumentItem, property);
            if(ServerPropertyValues.isCaseSensitive(property)) {
                // if case-sensitive, check if value is valid
                hasErrors = !validValues.contains(value);
            } else {
                // ignoring case, check if value is valid
                hasErrors = !validValues.stream().anyMatch(value::equalsIgnoreCase);
            }
            diagnosticType = ParserFileHelperUtil.isBootstrapPropertiesFile(textDocumentItem) ? 
                                            "INVALID_PROPERTY_VALUE" : "INVALID_VARIABLE_VALUE";
        } else if (ServerPropertyValues.usesIntegerRangeValue(property)) {
            Range<Integer> range = ServerPropertyValues.getIntegerRange(property);
            if (ServerPropertyValues.usesTimeUnit(property)) {
                // for purgeMinTime - with or without unit (h or H) is acceptable.
                try { // try parsing Integer if no units specified
                    hasErrors = !range.contains(Integer.parseInt(value));
                } catch (NumberFormatException e) { // contains unit (non-decimal char)
                    // only accept integer followed by h or H
                    Pattern integerAndTime = Pattern.compile("(^[0-9]+)([h|H]?)$");
                    Matcher matcher = integerAndTime.matcher(value);
                    if (matcher.find()) {
                        try {
                            int val = new BigInteger(matcher.group(1)).intValueExact();
                            hasErrors = !range.contains(val);
                        } catch (ArithmeticException ae) { // value too large for Integer
                            hasErrors = true;
                        }
                    } else { // did not contain or start with integer
                        hasErrors = true;
                    }
                }
            } else {
                try {
                    hasErrors = !range.contains(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    hasErrors = true;
                }
            }
            diagnosticType = ParserFileHelperUtil.isBootstrapPropertiesFile(textDocumentItem) ? 
                                            "INVALID_PROPERTY_INTEGER_RANGE" : "INVALID_VARIABLE_INTEGER_RANGE";
        } else if (ServerPropertyValues.usesPackageNames(property)) { // defines packages
            // simple check for comma-delimited list of Java packages
            Pattern packageList = Pattern.compile("^([a-z_]+((\\.[a-z]|\\._[0-9])[a-z0-9_]*)*)(,[a-z_]+((\\.[a-z]|\\._[0-9])[a-z0-9_]*)*)*$");
            Matcher matcher = packageList.matcher(value);
            if (!matcher.find()) {
                hasErrors = true;
            }
            diagnosticType = "INVALID_PACKAGE_LIST_FORMAT";
        }
        return;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return this.lineNumber;
    }

    public boolean hasErrors() {
        return hasErrors;
    }

    public String getKey() {
        return entry.getKey();
    }

    public String getValue() {
        return entry.getValue();
    }

    public String getDiagnosticType() {
        return diagnosticType;
    }
}
