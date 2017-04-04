package com.lakeel.altla.android.binding.propertybinder;


import com.lakeel.altla.android.binding.BindingMode;
import com.lakeel.altla.android.binding.Converter;
import com.lakeel.altla.android.binding.Property;
import com.lakeel.altla.android.binding.PropertyBinder;
import com.lakeel.altla.android.binding.Unbindable;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DefaultPropertyBinder implements PropertyBinder, Unbindable {

    private final PropertyBindingDefinition definition;

    private final View target;

    private final Property<?> source;

    private BindingMode mode = BindingMode.DEFAULT;

    private Converter converter = Converter.EMPTY;

    private boolean targetUpdateSuppressed;

    private boolean sourceUpdateSuppressed;

    private Property.OnValueChangedListener onValueChangedListener;

    public DefaultPropertyBinder(@NonNull PropertyBindingDefinition definition, @NonNull View target,
                                 @NonNull Property<?> source) {
        this.definition = definition;
        this.target = target;
        this.source = source;
    }

    @Override
    public final PropertyBinder mode(@NonNull BindingMode mode) {
        this.mode = mode;
        return this;
    }

    @Override
    public final PropertyBinder converter(@NonNull Converter converter) {
        this.converter = converter;
        return this;
    }

    @Override
    public PropertyBinder converter(@Nullable final Convert convert, @Nullable final ConvertBack convertBack) {
        converter = new Converter() {
            @Override
            public Object convert(Object value) {
                return convert == null ? value : convert.convert(value);
            }

            @Override
            public Object convertBack(Object value) {
                return convertBack == null ? value : convertBack.convertBack(value);
            }
        };
        return this;
    }

    @Override
    public Unbindable bind() {
        BindingMode mode =
                (this.mode == BindingMode.DEFAULT) ? definition.getDefaultBindingMode() : this.mode;

        if (mode == BindingMode.ONE_WAY || mode == BindingMode.TWO_WAY || mode == BindingMode.ONE_TIME) {
            updateTarget();
        }

        if (mode == BindingMode.ONE_WAY_TO_SOURCE) {
            updateSource();
        }

        if (mode == BindingMode.ONE_WAY || mode == BindingMode.TWO_WAY) {
            bindTarget();
        }

        if (mode == BindingMode.TWO_WAY || mode == BindingMode.ONE_WAY_TO_SOURCE) {
            bindSource();
        }

        return this;
    }

    @Override
    public void unbind() {
        if (onValueChangedListener != null) source.removeOnValueChangedListener(onValueChangedListener);
        unbindSource();
    }

    @NonNull
    protected PropertyBindingDefinition getDefinition() {
        return definition;
    }

    @NonNull
    protected View getTarget() {
        return target;
    }

    @NonNull
    protected Property<?> getSource() {
        return source;
    }

    @NonNull
    protected BindingMode getMode() {
        return mode;
    }

    @NonNull
    protected Converter getConverter() {
        return converter;
    }

    protected final void updateTarget() {
        sourceUpdateSuppressed = true;
        if (!targetUpdateSuppressed) {
            updateTargetCore();
        }
        sourceUpdateSuppressed = false;
    }

    protected final void updateSource() {
        targetUpdateSuppressed = true;
        if (!sourceUpdateSuppressed) {
            updateSourceCore();
        }
        targetUpdateSuppressed = false;
    }

    protected void updateTargetCore() {
        Method method = definition.getSetterMethod();
        if (method != null) {
            Object value = converter.convert(source.getAsObject());
            try {
                method.invoke(target, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                // TODO
                e.printStackTrace();
            }
        }
    }

    protected void updateSourceCore() {
        Method method = definition.getGetterMethod();
        if (method != null) {
            try {
                Object value = converter.convertBack(method.invoke(target));
                source.setAsObject(value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                // TODO
                e.printStackTrace();
            }
        }
    }

    protected void bindSource() {
    }

    protected void unbindSource() {
    }

    private void bindTarget() {
        onValueChangedListener = new Property.OnValueChangedListener() {
            @Override
            public void onValueChanged() {
                updateTarget();
            }
        };
        source.addOnValueChangedListener(onValueChangedListener);
    }
}