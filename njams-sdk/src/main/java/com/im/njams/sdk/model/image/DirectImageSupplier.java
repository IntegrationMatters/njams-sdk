package com.im.njams.sdk.model.image;

/**
 * An {@link ImageSupplier} implementation that takes and provides an already base64 encoded image.
 */
public class DirectImageSupplier extends ImageSupplier {

    private final String image;

    /**
     * Sole constructor
     * @param name The name of the image
     * @param iamgeAsBase64 The base64 encoded image
     */
    public DirectImageSupplier(String name, String iamgeAsBase64) {
        super(name);
        image = iamgeAsBase64;
    }

    @Override
    public String getBase64Image() {
        return image;
    }

}
