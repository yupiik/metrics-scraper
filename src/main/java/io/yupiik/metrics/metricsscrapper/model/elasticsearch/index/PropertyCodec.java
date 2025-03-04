package io.yupiik.metrics.metricsscrapper.model.elasticsearch.index;

import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;

public class PropertyCodec implements JsonCodec<Property> {

    private final Type type;

    public PropertyCodec() {
        this.type = new Types.ParameterizedTypeImpl(Property.class);
    }

    @Override
    public Type type() {
        return this.type;
    }

    @Override
    public Property read(final DeserializationContext context) throws IOException {
        return null;
    }

    @Override
    public void write(final Property value, final SerializationContext context) throws IOException {
        final String json = "\"%s\": {" + "\"toto\": \"%s\"" + "}";
        context.writer().write(String.format(json, value.name(), value.coucou()));
    }
}
