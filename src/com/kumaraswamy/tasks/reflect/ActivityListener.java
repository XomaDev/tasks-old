package com.kumaraswamy.tasks.reflect;

import com.google.appinventor.components.runtime.Component;

public class ActivityListener {
    public interface ComponentsCreated {
        void componentsCreated();
    }

    public interface EventRaised {
        void eventRaised(Component component, String eventName, Object[] parameters);
    }
}
