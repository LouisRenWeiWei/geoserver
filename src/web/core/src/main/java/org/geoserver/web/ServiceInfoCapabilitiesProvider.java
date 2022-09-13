/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.wicket.Component;
import org.geoserver.config.ServiceInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.Service;

/**
 * Contributes standard GetCapabilities links for all the versions in all the {@link ServiceInfo}
 * implementations available to the {@link GeoServerApplication} using the GeoServer's {@code /ows}
 * standard OpenGIS Web Service entry point as the root of the GetCapabilities request.
 *
 * @author Gabriel Roldan
 * @see CapabilitiesHomePagePanel
 */
public class ServiceInfoCapabilitiesProvider implements CapabilitiesHomePageLinkProvider {

    /** @see org.geoserver.web.CapabilitiesHomePageLinkProvider#getCapabilitiesComponent */
    @Override
    public Component getCapabilitiesComponent(final String id) {

        Set<String> skip = new HashSet<>();
        for (ServiceDescriptionProvider provider :
                GeoServerExtensions.extensions(ServiceDescriptionProvider.class)) {
            for (ServiceDescription service : provider.getServices(null, null)) {
                skip.add(service.getService());
            }
            for (ServiceLinkDescription link : provider.getServiceLinks(null, null)) {
                skip.add(link.getProtocol().toLowerCase());
            }
        }
        @SuppressWarnings("deprecation")
        List<org.geoserver.web.CapabilitiesHomePagePanel.CapsInfo> serviceInfoLinks =
                new ArrayList<>();

        for (Service service : GeoServerExtensions.extensions(Service.class)) {
            String serviceId = service.getId();
            if (skip.contains(serviceId.toLowerCase())) {
                continue;
            } else if (service.getCustomCapabilitiesLink() != null) {
                String capsLink = service.getCustomCapabilitiesLink();
                @SuppressWarnings("deprecation")
                org.geoserver.web.CapabilitiesHomePagePanel.CapsInfo ci =
                        new org.geoserver.web.CapabilitiesHomePagePanel.CapsInfo(
                                serviceId, service.getVersion(), capsLink);
                serviceInfoLinks.add(ci);
            } else if (service.getOperations().contains("GetCapabilities")) {
                String capsLink =
                        "../ows?service="
                                + serviceId
                                + "&version="
                                + service.getVersion().toString()
                                + "&request=GetCapabilities";
                @SuppressWarnings("deprecation")
                org.geoserver.web.CapabilitiesHomePagePanel.CapsInfo ci =
                        new org.geoserver.web.CapabilitiesHomePagePanel.CapsInfo(
                                serviceId, service.getVersion(), capsLink);
                serviceInfoLinks.add(ci);
            }
        }
        if (serviceInfoLinks.isEmpty()) {
            return null;
        }
        return new CapabilitiesHomePagePanel(id, serviceInfoLinks);
    }
}
