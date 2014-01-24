/*
 * Copyright (C) 2012 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.bonitasoft.engine.command.helper.designer;

import org.bonitasoft.engine.bpm.process.impl.ProcessDefinitionBuilder;

/**
 * @author Vincent Elcrin
 */
public class Transition {

    public void bind(String source, String target, Condition condition, ProcessDefinitionBuilder builder) {
        if(condition == null) {
            builder.addTransition(source, target);
        } else if(condition.isDefault()) {
            builder.addDefaultTransition(source, target);
        } else {
            builder.addTransition(source, target, condition.getExpression());
        }
    }
}
