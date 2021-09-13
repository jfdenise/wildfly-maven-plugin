/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wildfly.plugin.provision;
import java.util.HashMap;
import java.util.Map;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

public class CustomBasicComponentConfigurator extends BasicComponentConfigurator {
    public static class MyCustomConverter implements ConfigurationConverter {

        @Override
        public boolean canConvert(Class<?> type) {
           System.out.println("TYPE " + type);
           return ProvConfig.class.equals(type);
        }

        @Override
        public Object fromConfiguration(ConverterLookup cl, PlexusConfiguration pc, Class<?> type, Class<?> type1, ClassLoader cl1, ExpressionEvaluator ee) throws ComponentConfigurationException {
            return fromConfiguration(cl, pc, type, type1, cl1, ee, null);
        }

        @Override
        public Object fromConfiguration(ConverterLookup cl, PlexusConfiguration pc, Class<?> type, Class<?> type1, ClassLoader cl1, ExpressionEvaluator ee, ConfigurationListener cl2) throws ComponentConfigurationException {
            Map<String, Object> config = new HashMap<>();
            for(PlexusConfiguration c : pc.getChildren()) {
                config.put(c.getName(), getValue(c));
            }
            System.out.println("MAPPED CONFIG " + config);
            return new ProvConfig(config);
        }

        Object getValue(PlexusConfiguration pc) {
            if(pc.getChildCount() != 0) {
                return toMap(pc);
            }
            return pc.getValue();
        }

        Map<String, Object> toMap(PlexusConfiguration pc) {
            Map<String, Object> config = new HashMap<>();
            int i = 0;
            for (PlexusConfiguration c : pc.getChildren()) {
                Object value = getValue(c);
                String key = pc.getName();
                if (config.containsKey(key)) {
                    key = key + "-" + i;
                    i += 1;
                }
                config.put(c.getName(), getValue(c));
            }
            return config;
        }

    }
    @Override
    public void configureComponent(final Object component, final PlexusConfiguration configuration,
            final ExpressionEvaluator evaluator, final ClassRealm realm, final ConfigurationListener listener)
            throws ComponentConfigurationException {
        converterLookup.registerConverter((ConfigurationConverter) new MyCustomConverter());
        System.out.println("COMPONENT " + component);
        super.configureComponent(component, configuration, evaluator, realm, listener);
    }
}
