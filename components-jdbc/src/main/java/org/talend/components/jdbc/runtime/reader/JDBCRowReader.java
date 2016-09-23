// ============================================================================
//
// Copyright (C) 2006-2015 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.components.jdbc.runtime.reader;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.IndexedRecord;
import org.talend.components.api.component.runtime.AbstractBoundedReader;
import org.talend.components.api.component.runtime.Result;
import org.talend.components.api.container.RuntimeContainer;
import org.talend.components.api.exception.ComponentException;
import org.talend.components.api.exception.DataRejectException;
import org.talend.components.jdbc.runtime.JDBCRowSource;
import org.talend.components.jdbc.runtime.JDBCTemplate;
import org.talend.components.jdbc.tjdbcrow.TJDBCRowProperties;

public class JDBCRowReader extends AbstractBoundedReader<IndexedRecord> {

    protected TJDBCRowProperties properties;

    protected RuntimeContainer container;

    protected Connection conn;

    protected ResultSet resultSet;

    private transient Schema querySchema;

    private JDBCRowSource source;

    private PreparedStatement prepared_statement;

    private Statement statement;

    private Result result;

    private boolean useExistedConnection;

    private boolean dieOnError;

    public JDBCRowReader(RuntimeContainer container, JDBCRowSource source, TJDBCRowProperties props) {
        super(source);
        this.container = container;
        this.properties = props;
        this.source = (JDBCRowSource) getCurrentSource();

        this.dieOnError = props.dieOnError.getValue();

    }

    @SuppressWarnings("unused")
    private Schema getSchema() {
        if (querySchema == null) {
            querySchema = properties.main.schema.getValue();
        }
        return querySchema;
    }

    @Override
    public boolean start() throws IOException {
        // TODO need to adjust the key
        if (container != null) {
            container.setComponentData(container.getCurrentComponentId(), "QUERY", properties.sql.getValue());
        }

        result = new Result();

        try {
            conn = source.getConnection(container);
        } catch (ClassNotFoundException | SQLException e) {
            throw new ComponentException(e);
        }

        return true;
    }

    @Override
    public boolean advance() throws IOException {
        return false;// only one row
    }

    @Override
    public IndexedRecord getCurrent() throws NoSuchElementException {
        try {
            boolean usePreparedStatement = properties.usePreparedStatement.getValue();
            String sql = properties.sql.getValue();
            boolean propagateQueryResultSet = properties.propagateQueryResultSet.getValue();

            if (usePreparedStatement) {
                prepared_statement = conn.prepareStatement(sql);

                JDBCTemplate.setPreparedStatement(prepared_statement, properties.preparedStatementTable);

                if (propagateQueryResultSet) {
                    resultSet = prepared_statement.executeQuery();
                } else {
                    prepared_statement.execute();
                }
            } else {
                statement = conn.createStatement();

                if (propagateQueryResultSet) {
                    resultSet = statement.executeQuery(sql);
                } else {
                    statement.execute(sql);
                }
            }

            IndexedRecord output = handleSuccess(propagateQueryResultSet);

            return output;
        } catch (SQLException e) {
            if (dieOnError) {
                throw new ComponentException(e);
            } else {
                // TODO : log it
            }

            handleReject(e);
        }
        return null;
    }

    private IndexedRecord handleSuccess(boolean propagateQueryResultSet) {
        Schema outSchema = properties.schemaFlow.schema.getValue();
        IndexedRecord output = new GenericData.Record(outSchema);

        if (propagateQueryResultSet) {
            String columnName = properties.useColumn.getValue();
            for (Schema.Field outField : output.getSchema().getFields()) {
                if (outField.name().equals(columnName)) {
                    output.put(outField.pos(), resultSet);
                }
            }
        }

        return output;
    }

    private void handleReject(SQLException e) {
        Schema outSchema = properties.schemaReject.schema.getValue();
        IndexedRecord reject = new GenericData.Record(outSchema);

        for (Schema.Field outField : reject.getSchema().getFields()) {
            Object outValue = null;

            if ("errorCode".equals(outField.name())) {
                outValue = e.getSQLState();
            } else if ("errorMessage".equals(outField.name())) {
                outValue = e.getMessage();
            }

            reject.put(outField.pos(), outValue);
        }

        Map<String, Object> resultMessage = new HashMap<String, Object>();
        resultMessage.put("error", e.getMessage());
        resultMessage.put("talend_record", reject);
        throw new DataRejectException(resultMessage);
    }

    @Override
    public void close() throws IOException {
        try {
            if (prepared_statement != null) {
                prepared_statement.close();
                prepared_statement = null;
            }

            if (statement != null) {
                statement.close();
                statement = null;
            }

            if (!useExistedConnection && conn != null) {
                conn.commit();
                conn.close();
                conn = null;
            }
        } catch (SQLException e) {
            throw new ComponentException(e);
        }
    }

    @Override
    public Map<String, Object> getReturnValues() {
        return result.toMap();
    }

}
