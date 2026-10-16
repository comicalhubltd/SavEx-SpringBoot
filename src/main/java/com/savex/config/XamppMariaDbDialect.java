package com.savex.config;

import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.type.SqlTypes;

/** MariaDB represents JSON columns as checked LONGTEXT in JDBC metadata. */
public class XamppMariaDbDialect extends MariaDBDialect {
    @Override
    public boolean equivalentTypes(int first, int second) {
        return super.equivalentTypes(first, second)
            || (first == SqlTypes.JSON && second == SqlTypes.LONGVARCHAR)
            || (second == SqlTypes.JSON && first == SqlTypes.LONGVARCHAR);
    }
}
