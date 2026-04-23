package com.migration.scanner.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.Update;

final class SqlAstWalker extends StatementVisitorAdapter {
    private static final Set<String> ORACLE_FUNCTION_RISKS = Set.of(
        "NVL",
        "DECODE",
        "SYSDATE",
        "SYSTIMESTAMP",
        "TO_DATE",
        "TO_CHAR",
        "LISTAGG"
    );

    private final Set<String> columns;
    private final Set<String> joins;
    private final Set<String> risks;
    private final ExpressionVisitorAdapter expressionVisitor;

    SqlAstWalker(Set<String> columns, Set<String> joins, Set<String> risks) {
        this.columns = columns;
        this.joins = joins;
        this.risks = risks;
        this.expressionVisitor = new ExpressionVisitorAdapter() {
            @Override
            public void visit(Column column) {
                String tablePrefix = column.getTable() != null && column.getTable().getName() != null
                    ? column.getTable().getName() + "."
                    : "";
                SqlAstWalker.this.columns.add((tablePrefix + column.getColumnName()).toUpperCase(Locale.ROOT));
            }

            @Override
            public void visit(Function function) {
                String name = function.getName() == null ? "" : function.getName().toUpperCase(Locale.ROOT);
                if (ORACLE_FUNCTION_RISKS.contains(name)) {
                    risks.add(name);
                }
                super.visit(function);
            }
        };
    }

    @Override
    public void visit(Select select) {
        walkSelectBody(select.getSelectBody());
        if (select.getWithItemsList() != null) {
            for (WithItem withItem : select.getWithItemsList()) {
                walkSelectBody(withItem);
            }
        }
    }

    @Override
    public void visit(Insert insert) {
        if (insert.getColumns() != null) {
            insert.getColumns().forEach(column -> columns.add(column.getColumnName().toUpperCase(Locale.ROOT)));
        }
        if (insert.getSelect() != null) {
            walkSelectBody(insert.getSelect().getSelectBody());
        }
    }

    @Override
    public void visit(Update update) {
        if (update.getColumns() != null) {
            update.getColumns().forEach(column -> columns.add(column.getColumnName().toUpperCase(Locale.ROOT)));
        }
        if (update.getWhere() != null) {
            update.getWhere().accept(expressionVisitor);
        }
        if (update.getFromItem() != null) {
            walkFromItem(update.getFromItem());
        }
        if (update.getJoins() != null) {
            walkJoins(update.getJoins());
        }
    }

    @Override
    public void visit(Delete delete) {
        if (delete.getWhere() != null) {
            delete.getWhere().accept(expressionVisitor);
        }
    }

    @Override
    public void visit(Merge merge) {
        risks.add("MERGE");
        if (merge.getOnCondition() != null) {
            merge.getOnCondition().accept(expressionVisitor);
        }
    }

    private void walkSelectBody(Select body) {
        if (body == null) {
            return;
        }
        if (body instanceof PlainSelect) {
            PlainSelect plain = (PlainSelect) body;
            if (plain.getSelectItems() != null) {
                for (SelectItem<?> item : plain.getSelectItems()) {
                    if (item.getExpression() != null) {
                        item.getExpression().accept(expressionVisitor);
                    }
                }
            }
            walkFromItem(plain.getFromItem());
            walkJoins(plain.getJoins());
            if (plain.getWhere() != null) {
                plain.getWhere().accept(expressionVisitor);
            }
            if (plain.getHaving() != null) {
                plain.getHaving().accept(expressionVisitor);
            }
            if (plain.getGroupBy() != null && plain.getGroupBy().getGroupByExpressionList() != null) {
                for (Object expr : plain.getGroupBy().getGroupByExpressionList()) {
                    if (expr instanceof net.sf.jsqlparser.expression.Expression) {
                        ((net.sf.jsqlparser.expression.Expression) expr).accept(expressionVisitor);
                    }
                }
            }
            if (plain.getOrderByElements() != null) {
                for (OrderByElement orderBy : plain.getOrderByElements()) {
                    if (orderBy.getExpression() != null) {
                        orderBy.getExpression().accept(expressionVisitor);
                    }
                }
            }
            return;
        }
        if (body instanceof SetOperationList) {
            SetOperationList setList = (SetOperationList) body;
            setList.getSelects().forEach(this::walkSelectBody);
            return;
        }
        if (body instanceof WithItem) {
            WithItem withItem = (WithItem) body;
            walkSelectBody(withItem.getSelectBody());
            return;
        }
        if (body instanceof Values) {
            Values values = (Values) body;
            if (values.getExpressions() != null) {
                for (Object expr : values.getExpressions()) {
                    if (expr instanceof net.sf.jsqlparser.expression.Expression) {
                        ((net.sf.jsqlparser.expression.Expression) expr).accept(expressionVisitor);
                    }
                }
            }
        }
    }

    private void walkFromItem(FromItem fromItem) {
        if (fromItem == null) {
            return;
        }
        if (fromItem instanceof ParenthesedSelect) {
            walkSelectBody(((ParenthesedSelect) fromItem).getSelect());
        }
    }

    private void walkJoins(List<Join> joinList) {
        if (joinList == null) {
            return;
        }
        for (Join join : joinList) {
            this.joins.add(describeJoin(join));
            if (join.getRightItem() != null) {
                walkFromItem(join.getRightItem());
            }
            if (join.getOnExpressions() != null) {
                join.getOnExpressions().forEach(expr -> expr.accept(expressionVisitor));
            }
        }
    }

    private String describeJoin(Join join) {
        List<String> flags = new ArrayList<>();
        if (join.isLeft()) {
            flags.add("LEFT");
        } else if (join.isRight()) {
            flags.add("RIGHT");
        } else if (join.isFull()) {
            flags.add("FULL");
        } else if (join.isInner()) {
            flags.add("INNER");
        } else if (join.isCross()) {
            flags.add("CROSS");
        } else {
            flags.add("JOIN");
        }
        return String.join("_", flags);
    }
}
