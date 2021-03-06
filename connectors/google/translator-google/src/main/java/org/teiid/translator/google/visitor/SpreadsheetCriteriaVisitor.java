/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.translator.google.visitor;

import static org.teiid.language.SQLConstants.Reserved.*;

import org.teiid.core.types.DataTypeManager;
import org.teiid.core.util.StringUtil;
import org.teiid.language.Comparison;
import org.teiid.language.Comparison.Operator;
import org.teiid.language.Condition;
import org.teiid.language.Function;
import org.teiid.language.Like;
import org.teiid.language.Literal;
import org.teiid.language.visitor.SQLStringVisitor;
import org.teiid.translator.TypeFacility;
import org.teiid.translator.google.SpreadsheetExecutionFactory;
import org.teiid.translator.google.api.SpreadsheetOperationException;
import org.teiid.translator.google.api.metadata.SpreadsheetInfo;

/**
 * Base visitor for criteria
 * 
 * @author felias
 * 
 */
public class SpreadsheetCriteriaVisitor extends SQLStringVisitor {

	protected String worksheetKey;
	protected String criteriaQuery;
	protected SpreadsheetInfo info;
	protected String worksheetTitle;

	public SpreadsheetCriteriaVisitor(SpreadsheetInfo info) {
		this.info = info;
	}

	public void visit(Literal obj) {
	    if (!isUpdate()) {
	        super.visit(obj);
	        return;
	    }
		if (obj.getValue() == null) {
			buffer.append(NULL);
			return;
		}
		Class<?> type = obj.getType();
		if (Number.class.isAssignableFrom(type)) {
			buffer.append(obj.toString());
			return;
		} else if (obj.getType().equals(DataTypeManager.DefaultDataClasses.DATE) 
		        || obj.getType().equals(DataTypeManager.DefaultDataClasses.BOOLEAN)) {
			buffer.append(obj.getValue().toString()); 
			return;
		} else if (obj.getType().equals(DataTypeManager.DefaultDataClasses.TIME)
		        || obj.getType().equals(DataTypeManager.DefaultDataClasses.TIMESTAMP)) {
            throw new SpreadsheetOperationException(SpreadsheetExecutionFactory.UTIL.gs("time_not_supported")); //$NON-NLS-1$
        } else {
			buffer.append("\""); //$NON-NLS-1$
			buffer.append(StringUtil.replace(obj.getValue().toString(), "\"", "\"\"")); //$NON-NLS-1$ //$NON-NLS-2$
			buffer.append("\""); //$NON-NLS-1$
			return;
		}
	}

	public void visit(Like obj) {
	    if (isUpdate()) {
	        throw new SpreadsheetOperationException("Like is not supported in DELETE and UPDATE queires");
	    }
	    super.visit(obj);
	}
	
	@Override
	public void visit(Function obj) {
	    if (isUpdate()) {
	        throw new SpreadsheetOperationException("Function is not supported in DELETE and UPDATE queires");
	    }
	    super.visit(obj);
	}

	@Override
	protected String replaceElementName(String group, String element) {
		return element.toLowerCase();
	}

	public String getWorksheetKey() {
		return worksheetKey;
	}

	public String getCriteriaQuery() {
		return criteriaQuery;
	}

	public void setCriteriaQuery(String criteriaQuery) {
		this.criteriaQuery = criteriaQuery;
	}

	public String getWorksheetTitle() {
		return worksheetTitle;
	}
	
	public void translateWhere(Condition condition) {
	    if (condition != null) {
	        StringBuilder temp = this.buffer;
	        this.buffer = new StringBuilder();
	        append(condition);
	        criteriaQuery = buffer.toString();
	        this.buffer = temp;
	    }
	}
	
	public void visit(Comparison obj) {
	    boolean addNot = false;
	    if ((!isUpdate() || obj.getLeftExpression().getType() != TypeFacility.RUNTIME_TYPES.STRING)
	            && (obj.getOperator() == Operator.NE 
	            || obj.getOperator() == Operator.LT 
	            || obj.getOperator() == Operator.LE
	            || (obj.getOperator() == Operator.EQ && !(obj.getRightExpression() instanceof Literal)))) {
	        addNot = true;
	        buffer.append("("); //$NON-NLS-1$
	    }
	    super.visit(obj);
	    if (addNot) {
    	    buffer.append(" AND "); //$NON-NLS-1$
            visitNode(obj.getLeftExpression());
            if (!isUpdate()) {
                buffer.append(" IS NOT NULL)"); //$NON-NLS-1$
            } else {
                buffer.append(" <> \"\")"); //$NON-NLS-1$
            }
	    }
	}
	
	protected boolean isUpdate() {
	    return true;
	}
}
