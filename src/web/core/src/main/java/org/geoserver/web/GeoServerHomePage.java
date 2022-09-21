/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web;

import static org.geoserver.catalog.Predicates.acceptAll;

import com.google.common.base.Stopwatch;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.wicket.Component;
import org.apache.wicket.Localizer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.mapper.parameter.INamedParameters;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.string.StringValue;
import org.apache.wicket.util.string.Strings;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.DefaultCatalogFacade;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.config.ContactInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.config.ServiceInfo;
import org.geoserver.config.SettingsInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.AdminRequest;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.util.InternationalStringUtils;
import org.geoserver.web.data.layer.LayerPage;
import org.geoserver.web.data.layer.NewLayerPage;
import org.geoserver.web.data.layer.PublishedChoiceRenderer;
import org.geoserver.web.data.layergroup.LayerGroupEditPage;
import org.geoserver.web.data.layergroup.LayerGroupPage;
import org.geoserver.web.data.store.NewDataPage;
import org.geoserver.web.data.store.StorePage;
import org.geoserver.web.data.workspace.WorkspaceChoiceNameRenderer;
import org.geoserver.web.data.workspace.WorkspaceNewPage;
import org.geoserver.web.data.workspace.WorkspacePage;
import org.geoserver.web.data.workspace.WorkspacesModel;
import org.geoserver.web.spring.security.GeoServerSession;
import org.geoserver.web.wicket.Select2DropDownChoice;
import org.geotools.feature.NameImpl;
import org.opengis.filter.Filter;
import org.opengis.util.InternationalString;
import org.springframework.security.core.Authentication;

/**
 * Home page, shows introduction for each kind of service along with any service links.
 *
 * <p>This page uses the {@link ServiceDescriptionProvider} extension point to allow other modules
 * to describe web services, and the web service links.
 *
 * <p>The {@link CapabilitiesHomePageLinkProvider} extension point enables other modules to
 * contribute components. The default {@link ServiceInfoCapabilitiesProvider} contributes the
 * capabilities links for all the available {@link ServiceInfo} implementations that were not
 * covered by the ServiceDescriptionProvider extensions. Other extension point implementations may
 * contribute service description document links not backed by ServiceInfo objects.
 *
 * <p>The {@link GeoServerHomePageContentProvider} is used by modules to contribute information,
 * status and warnings.
 *
 * <p>The page has built-in functionality providing administrators with a configuration summary.
 *
 * <p>This page can change between global service, workspace service and layer service.
 *
 * @author Andrea Aime - TOPP
 */
public class GeoServerHomePage extends GeoServerBasePage implements GeoServerUnlockablePage {

    /** Context workspace for displayed web services, or null for global services */
    private String workspace = null;

    /** Selected WorkspaceInfo */
    private WorkspaceInfo workspaceInfo;

    /** Context layer / layergroup for displayed web services (optional) */
    private String layer = null;

    /** Selected LayerInfo or LayerGroupInfo */
    private PublishedInfo layerInfo = null;

    public GeoServerHomePage() {
        homeInit();
    }

    public GeoServerHomePage(PageParameters parameters) {
        super(parameters);
        homeInit();
    }

    private void homeInit() {

        GeoServer gs = getGeoServer();

        Authentication auth = getSession().getAuthentication();
        boolean admin = isAdmin(auth);

        if (!admin) {
            // clear admin request so it does not interfere with
            // catalogue access of workspaces and layers
            AdminRequest.abort();
        }

        if (getPageParameters() != null && !getPageParameters().isEmpty()) {
            StringValue workspaceParam = getPageParameters().get("workspace");
            if (workspaceParam == null
                    || workspaceParam.isEmpty()
                    || Strings.isEmpty(workspaceParam.toString())) {
                this.workspace = null; // list global services
            } else {
                this.workspace = workspaceParam.toString();
            }

            StringValue layerParam = getPageParameters().get("layer");
            if (layerParam == null
                    || layerParam.isEmpty()
                    || Strings.isEmpty(layerParam.toString())) {
                this.layer = null; // for all services
            } else {
                this.layer = layerParam.toString();
            }
        }

        if (this.workspace != null) {
            if (this.workspaceInfo == null) {
                this.workspaceInfo = gs.getCatalog().getWorkspaceByName(this.workspace);
            }
        }
        if (this.layer != null) {
            if (this.layerInfo == null) {
                this.layerInfo = layerInfo(workspaceInfo, this.layer);
            }
            if (layerInfo != null) {
                String prefixedName = layerInfo.prefixedName();
                if (prefixedName != null && prefixedName.contains(":")) {
                    String prefix = prefixedName.substring(0, prefixedName.indexOf(":"));
                    if (this.workspace == null || !this.workspace.equals(prefix)) {
                        workspaceInfo = getGeoServer().getCatalog().getWorkspaceByName(prefix);
                    }
                }
            }
        }

        ContactInfo contactInfo = gs.getSettings().getContact();
        if (workspaceInfo != null) {
            SettingsInfo settings = gs.getSettings(workspaceInfo);
            if (settings != null) {
                contactInfo = settings.getContact();
            }
        }

        Form<GeoServerHomePage> form = new Form<>("form");
        SubmitLink refresh =
                new SubmitLink("refresh") {
                    @Override
                    public void onSubmit() {
                        setResponsePage(
                                GeoServerHomePage.class,
                                new PageParameters()
                                        .set(
                                                "workspace",
                                                workspaceInfo != null
                                                        ? workspaceInfo.getName()
                                                        : null,
                                                0,
                                                INamedParameters.Type.QUERY_STRING)
                                        .set(
                                                "layer",
                                                layerInfo != null ? layerInfo.getName() : null,
                                                1,
                                                INamedParameters.Type.QUERY_STRING));
                    }
                };
        refresh.setVisible(false);
        form.add(refresh);
        form.setDefaultButton(refresh);
        add(form);

        @SuppressWarnings("PMD.UseDiamondOperator") // java 8 compiler cannot infer type
        final DropDownChoice<WorkspaceInfo> workspaceField =
                new Select2DropDownChoice<>(
                        "workspace",
                        new PropertyModel<>(this, "workspaceInfo"),
                        new WorkspacesModel(),
                        new WorkspaceChoiceNameRenderer());
        workspaceField.setNullValid(true);
        workspaceField.setRequired(false);
        form.add(workspaceField);

        final DropDownChoice<PublishedInfo> layerField =
                new Select2DropDownChoice<>(
                        "layer",
                        new PropertyModel<>(this, "layerInfo"),
                        getLayerNames(),
                        new PublishedChoiceRenderer());
        layerField.setNullValid(true);
        layerField.setRequired(false);
        form.add(layerField);

        workspaceField.add(
                new AjaxFormComponentUpdatingBehavior("change") {
                    private static final long serialVersionUID = 5871428962450362668L;

                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        setResponsePage(
                                GeoServerHomePage.class,
                                new PageParameters()
                                        .set(
                                                "workspace",
                                                workspaceField.getInput(),
                                                0,
                                                INamedParameters.Type.QUERY_STRING)
                                        .set("layer", null, 1, INamedParameters.Type.QUERY_STRING));
                    }
                });

        layerField.add(
                new AjaxFormComponentUpdatingBehavior("change") {
                    private static final long serialVersionUID = 5871428962450362669L;

                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        String layerName = layerField.getInput();
                        if (layerName == null && layerField.getModelObject() != null) {
                            // No input selected, check model object displayed
                            layerName = layerField.getModelObject().prefixedName();
                        }
                        String workspaceName = workspaceField.getInput();
                        if (workspaceName == null && workspaceField.getModelObject() != null) {
                            // No input selected, check model object displayed
                            workspaceName = workspaceField.getModelObject().getName();
                        }
                        setResponsePage(
                                GeoServerHomePage.class,
                                new PageParameters()
                                        .set(
                                                "workspace",
                                                toWorkspace(workspaceName, layerName),
                                                0,
                                                INamedParameters.Type.QUERY_STRING)
                                        .set(
                                                "layer",
                                                toLayer(workspaceName, layerName),
                                                1,
                                                INamedParameters.Type.QUERY_STRING));
                    }
                });

        Locale locale = getLocale();

        String welcomeText = contactInfo.getWelcome();
        Label welcomeMessage = new Label("welcome", welcomeText);
        welcomeMessage.setVisible(StringUtils.isNotBlank(welcomeText));
        add(welcomeMessage);

        add(belongsTo(contactInfo, locale));

        add(footerMessage(contactInfo, locale));
        add(footerContact(contactInfo, locale));

        if (admin) {
            // show admin some additional details
            add(adminOverview());
        } else {
            // add catalogLinks placeholder (even when not admin) to identify this page location
            add(placeholderLabel("catalogLinks"));
        }

        // additional content provided by plugins across the geoserver codebase
        // for example security warnings to admin
        add(additionalHomePageContent());

        List<ServiceDescription> serviceDescriptions = new ArrayList<>();
        List<ServiceLinkDescription> serviceLinks = new ArrayList<>();
        for (ServiceDescriptionProvider provider :
                getGeoServerApplication().getBeansOfType(ServiceDescriptionProvider.class)) {
            serviceDescriptions.addAll(provider.getServices(workspaceInfo, layerInfo));
            serviceLinks.addAll(provider.getServiceLinks(workspaceInfo, layerInfo));
        }
        ServicesPanel serviceList =
                new ServicesPanel("serviceList", serviceDescriptions, serviceLinks, admin);
        add(serviceList);
        if (serviceDescriptions.isEmpty() && serviceLinks.isEmpty()) {
            serviceList.setVisible(false);
        }
        // service capabilities title only shown if needed
        Localizer localizer = GeoServerApplication.get().getResourceSettings().getLocalizer();

        final Label serviceCapabilitiesTitle =
                new Label(
                        "serviceCapabilities",
                        localizer.getString("GeoServerHomePage.serviceCapabilities", this));

        serviceCapabilitiesTitle.setVisible(false);
        add(serviceCapabilitiesTitle);

        final IModel<List<CapabilitiesHomePageLinkProvider>> capsProviders =
                getContentProviders(CapabilitiesHomePageLinkProvider.class);

        ListView<CapabilitiesHomePageLinkProvider> capsView =
                new ListView<CapabilitiesHomePageLinkProvider>("providedCaps", capsProviders) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void populateItem(ListItem<CapabilitiesHomePageLinkProvider> item) {
                        CapabilitiesHomePageLinkProvider provider = item.getModelObject();
                        Component capsList = null;
                        if (!(provider instanceof ServiceDescriptionProvider)) {
                            capsList = provider.getCapabilitiesComponent("capsList");
                            if (capsList != null) {
                                // provider has something to contirnute so service capabilities
                                // heading required
                                serviceCapabilitiesTitle.setVisible(true);
                            }
                        }
                        if (capsList == null) {
                            capsList = placeholderLabel("capsList");
                        }
                        item.add(capsList);
                    }
                };
        add(capsView);
    }

    @Override
    protected String getDescription() {
        Locale locale = getLocale();

        Catalog catalog = getCatalog();

        NumberFormat numberFormat = NumberFormat.getIntegerInstance(locale);
        numberFormat.setGroupingUsed(true);

        Filter allWorkspaces = acceptAll();
        int layerCount = countLayerNames();
        int workspaceCount = catalog.count(WorkspaceInfo.class, allWorkspaces);

        String userName = GeoServerSession.get().getUsername();

        HashMap<String, String> params = new HashMap<>();
        params.put("workspaceCount", numberFormat.format(workspaceCount));
        params.put("layerCount", numberFormat.format(layerCount));
        params.put("user", userName);

        boolean isGlobal = getGeoServer().getGlobal().isGlobalServices();

        StringBuilder builder = new StringBuilder();

        if (layerInfo != null && layerInfo instanceof LayerInfo) {
            params.put("layerName", layerInfo.getName());
            builder.append(
                    new StringResourceModel(
                                    "GeoServerHomePage.descriptionLayer",
                                    this,
                                    new Model<HashMap<String, String>>(params))
                            .getString());
        } else if (layerInfo != null && layerInfo instanceof LayerGroupInfo) {
            params.put("layerName", layerInfo.getName());

            LayerGroupInfo layerGroup = (LayerGroupInfo) layerInfo;
            if (layerGroup.getMode() == LayerGroupInfo.Mode.OPAQUE_CONTAINER
                    || layerGroup.getMode() == LayerGroupInfo.Mode.SINGLE) {
                builder.append(
                        new StringResourceModel(
                                        "GeoServerHomePage.descriptionLayer",
                                        this,
                                        new Model<HashMap<String, String>>(params))
                                .getString());
            } else {
                builder.append(
                        new StringResourceModel(
                                        "GeoServerHomePage.descriptionLayerGroup",
                                        this,
                                        new Model<HashMap<String, String>>(params))
                                .getString());
            }
        } else if (workspaceInfo != null) {
            params.put("workspaceName", workspaceInfo.getName());

            builder.append(
                    new StringResourceModel(
                                    "GeoServerHomePage.descriptionWorkspace",
                                    this,
                                    new Model<HashMap<String, String>>(params))
                            .getString());
        } else if (isGlobal) {
            builder.append(
                    new StringResourceModel(
                                    "GeoServerHomePage.descriptionGlobal",
                                    this,
                                    new Model<HashMap<String, String>>(params))
                            .getString());
        } else {
            builder.append(
                    new StringResourceModel(
                                    "GeoServerHomePage.descriptionGlobalOff",
                                    this,
                                    new Model<HashMap<String, String>>(params))
                            .getString());
        }

        builder.append(" ");
        if (isGlobal) {
            builder.append(new StringResourceModel("GeoServerHomePage.globalOn", this).getString());
        } else {
            builder.append(
                    new StringResourceModel("GeoServerHomePage.globalOff", this).getString());
        }
        return builder.toString();
    }

    /**
     * Additional content provided by plugins across the geoserver codebase for example security
     * warnings to admin
     *
     * @return ListView processing {@link GeoServerHomePageContentProvider} components
     */
    private ListView<GeoServerHomePageContentProvider> additionalHomePageContent() {
        final IModel<List<GeoServerHomePageContentProvider>> contentProviders =
                getContentProviders(GeoServerHomePageContentProvider.class);

        return new ListView<GeoServerHomePageContentProvider>(
                "contributedContent", contentProviders) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<GeoServerHomePageContentProvider> item) {
                GeoServerHomePageContentProvider provider = item.getModelObject();
                Component extraContent = provider.getPageBodyComponent("contentList");
                if (null == extraContent) {
                    extraContent = placeholderLabel("contentList");
                }
                item.add(extraContent);
            }
        };
    }

    private Label placeholderLabel(String wicketId) {
        Label placeHolder = new Label(wicketId);
        placeHolder.setVisible(false);
        return placeHolder;
    }

    private Fragment adminOverview() {
        Stopwatch sw = Stopwatch.createStarted();
        try {
            Fragment catalogLinks = new Fragment("catalogLinks", "catalogLinksFragment", this);
            Catalog catalog = getCatalog();

            NumberFormat numberFormat = NumberFormat.getIntegerInstance(getLocale());
            numberFormat.setGroupingUsed(true);

            Filter layerFilter = acceptAll();
            Filter groupFilter = acceptAll();
            Filter storeFilter = acceptAll();
            Filter allWorkspaces = acceptAll();
            if( workspaceInfo != null){
                layerFilter = Predicates.equal("resource.namespace.prefix", workspaceInfo.getName());
                groupFilter = Predicates.equal("workspace.name", workspaceInfo.getName());
                storeFilter = groupFilter = Predicates.equal("workspace.name", workspaceInfo.getName());
            }
            int layerCount = catalog.count(LayerInfo.class, layerFilter);
            int groupCount = catalog.count(LayerGroupInfo.class, groupFilter);
            int storesCount = catalog.count(StoreInfo.class, storeFilter);
            int wsCount = workspaceInfo != null ? 1 : catalog.count(WorkspaceInfo.class, allWorkspaces);

            catalogLinks.add(
                    new BookmarkablePageLink<LayerPage>("layersLink", LayerPage.class)
                            .add(new Label("nlayers", numberFormat.format(layerCount))));
            catalogLinks.add(
                    new BookmarkablePageLink<NewLayerPage>("addLayerLink", NewLayerPage.class));

            catalogLinks.add(
                    new BookmarkablePageLink<LayerGroupPage>("groupsLink", LayerGroupPage.class)
                            .add(new Label("ngroups", numberFormat.format(groupCount))));
            catalogLinks.add(
                    new BookmarkablePageLink<LayerGroupEditPage>("addGroupLink", LayerGroupEditPage.class));

            catalogLinks.add(
                    new BookmarkablePageLink<StorePage>("storesLink", StorePage.class)
                            .add(new Label("nstores", numberFormat.format(storesCount))));
            catalogLinks.add(
                    new BookmarkablePageLink<NewDataPage>("addStoreLink", NewDataPage.class));

            catalogLinks.add(
                    new BookmarkablePageLink<WorkspacePage>("workspacesLink", WorkspacePage.class)
                            .add(new Label("nworkspaces", numberFormat.format(wsCount))));
            catalogLinks.add(
                    new BookmarkablePageLink<WorkspaceNewPage>(
                            "addWorkspaceLink", WorkspaceNewPage.class));
            return catalogLinks;
        } finally {
            sw.stop();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(
                        "Admin summary of catalog links took " + sw.elapsed().toMillis() + " ms");
            }
        }
    }

    /**
     * Organization link to online resource, or placeholder if organization not provided.
     *
     * @param contactInfo
     * @param locale
     * @return organization link to online resource.
     */
    private Label belongsTo(ContactInfo contactInfo, Locale locale) {
        InternationalString onlineResource =
                InternationalStringUtils.growable(
                        contactInfo.getInternationalOnlineResource(),
                        InternationalStringUtils.firstNonBlank(
                                contactInfo.getOnlineResource(),
                                getGeoServer().getSettings().getOnlineResource()));

        InternationalString organization =
                InternationalStringUtils.growable(
                        contactInfo.getInternationalContactOrganization(),
                        contactInfo.getContactOrganization());

        if (organization == null || onlineResource == null) {
            return placeholderLabel("belongsTo");
        }
        HashMap<String, String> params = new HashMap<>();
        params.put("organization", StringEscapeUtils.escapeHtml4(organization.toString(locale)));
        params.put(
                "onlineResource", StringEscapeUtils.escapeHtml4(onlineResource.toString(locale)));

        Label belongsToMessage =
                new Label(
                        "belongsTo",
                        new StringResourceModel(
                                "GeoServerHomePage.belongsTo",
                                this,
                                new Model<HashMap<String, String>>(params)));
        belongsToMessage.setEscapeModelStrings(false);
        return belongsToMessage;
    }

    private Label footerMessage(ContactInfo contactInfo, Locale locale) {
        String version = String.valueOf(new ResourceModel("version").getObject());

        HashMap<String, String> params = new HashMap<>();
        params.put("version", version);

        Label footerMessage =
                new Label(
                        "footerMessage",
                        new StringResourceModel(
                                "GeoServerHomePage.footer",
                                this,
                                new Model<HashMap<String, String>>(params)));

        footerMessage.setEscapeModelStrings(false);
        return footerMessage;
    }

    private Label footerContact(ContactInfo contactInfo, Locale locale) {
        InternationalString contactEmailText =
                InternationalStringUtils.growable(
                        contactInfo.getInternationalContactEmail(), contactInfo.getContactEmail());

        String contactEmail = contactEmailText.toString(locale);

        if (Strings.isEmpty(contactEmail)) {
            return placeholderLabel("footerContact");
        }
        HashMap<String, String> params = new HashMap<>();
        params.put("contactEmail", StringEscapeUtils.escapeHtml4(contactEmail));
        Label message =
                new Label(
                        "footerContact",
                        new StringResourceModel(
                                "GeoServerHomePage.footerContact",
                                this,
                                new Model<HashMap<String, String>>(params)));

        message.setEscapeModelStrings(false);
        return message;
    }

    public WorkspaceInfo getWorkspaceInfo() {
        return workspaceInfo;
    }

    public void setWorkspaceInfo(WorkspaceInfo workspaceInfo) {
        this.workspaceInfo = workspaceInfo;
    }

    public PublishedInfo getLayerInfo() {
        return layerInfo;
    }

    public void setLayerInfo(PublishedInfo layerInfo) {
        this.layerInfo = layerInfo;
    }

    /**
     * Count of PublishedInfo (ie layer or layergroup) taking the current workspace and global
     * services into account.
     *
     * @return Count of addressable layers
     */
    private int countLayerNames() {
        Catalog catalog = getCatalog();
        String workspaceName = workspaceInfo != null ? workspaceInfo.getName() : null;

        return catalog.count(PublishedInfo.class, getLayerFilter(workspaceName));
    }

    /**
     * Layers, filtered by workspaceInfo prefix if available.
     *
     * Layers are listed sorted by prefix name order.
     *
     * @return layers, filtered by workspaceInfo prefix if available.
     */
    private List<PublishedInfo> getLayerNames() {
        List<PublishedInfo> layers = new ArrayList<>();
        Catalog catalog = getCatalog();

        String worksapceName =
                workspaceInfo != null ? workspaceInfo.getName() : null;

        try (CloseableIterator<PublishedInfo> it =
                    catalog.list(PublishedInfo.class, getLayerFilter(worksapceName))) {
            while (it.hasNext()) {
                PublishedInfo layer = it.next();
                layers.add(layer);
            }
        }
        layers.sort((o1, o2) -> o1.prefixedName().compareTo(o2.prefixedName()));
        return layers;
    }

    private <T> IModel<List<T>> getContentProviders(final Class<T> providerClass) {
        IModel<List<T>> providersModel =
                new LoadableDetachableModel<List<T>>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected List<T> load() {
                        GeoServerApplication app = getGeoServerApplication();
                        List<T> providers = app.getBeansOfType(providerClass);
                        return providers;
                    }
                };
        return providersModel;
    }

    /** Checks if the current user is authenticated and is the administrator */
    private boolean isAdmin(Authentication authentication) {

        return GeoServerExtensions.bean(GeoServerSecurityManager.class)
                .checkAuthenticationForAdminRole(authentication);
    }

    /**
     * Look up published info using page workspace / layer context (see {@code
     * LocalWorkspaceCallback}).
     *
     * @param workspaceInfo Name of workspace
     * @param layerName Name of layer or layer group
     * @return PublishedInfo representing layer info or group info, or {@code null} if not found
     */
    protected PublishedInfo layerInfo(WorkspaceInfo workspaceInfo, String layerName) {
        if (layerName == null) {
            return null;
        }
        Catalog catalog = getGeoServer().getCatalog();
        if (workspaceInfo != null) {
            NamespaceInfo namespaceInfo = catalog.getNamespaceByPrefix(workspaceInfo.getName());
            LayerInfo layerInfo =
                    catalog.getLayerByName(new NameImpl(namespaceInfo.getURI(), layerName));
            if (layerInfo != null) {
                return layerInfo;
            }
            LayerGroupInfo groupInfo = catalog.getLayerGroupByName(workspaceInfo, layerName);
            return groupInfo;
        } else {
            LayerInfo layerInfo = catalog.getLayerByName(layerName);
            if (layerInfo != null) {
                return layerInfo;
            }
            LayerGroupInfo groupInfo =
                    catalog.getLayerGroupByName(DefaultCatalogFacade.NO_WORKSPACE, layerName);
            if (groupInfo != null) {
                return groupInfo;
            }
            groupInfo = catalog.getLayerGroupByName(DefaultCatalogFacade.ANY_WORKSPACE, layerName);
            return groupInfo;
        }
    }

    /**
     * Predicate construct to efficently query catalog for PublishedInfo suitable for interaction.
     *
     * @param workspaceName Optional workspace name to limit search to a single workspace
     * @return Filter for use with catalog.
     */
    protected Filter getLayerFilter(String workspaceName) {

        // need to get only advertised and enabled layers
        Filter isLayerInfo = Predicates.isInstanceOf(LayerInfo.class);
        Filter isLayerGroupInfo = Predicates.isInstanceOf(LayerGroupInfo.class);


        Filter enabledFilter = Predicates.equal("resource.enabled", true);
        Filter storeEnabledFilter = Predicates.equal("resource.store.enabled", true);
        Filter advertisedFilter = Predicates.equal("resource.advertised", true);
        Filter enabledLayerGroup = Predicates.equal("enabled", true);
        Filter advertisedLayerGroup = Predicates.equal("advertised", true);

        // return only layer groups that are not containers
//        Filter nonContainerGroup =
//                Predicates.or(
//                        Predicates.equal("mode", LayerGroupInfo.Mode.EO),
//                        Predicates.equal("mode", LayerGroupInfo.Mode.NAMED),
//                        Predicates.equal("mode", LayerGroupInfo.Mode.OPAQUE_CONTAINER),
//                        Predicates.equal("mode", LayerGroupInfo.Mode.SINGLE));

        // Filter for the Layers
        Filter layerFilter;
        if (workspaceName != null) {
            Filter workspaceLayerFilter = Predicates.equal("resource.namespace.prefix", workspaceName);
            layerFilter =
                    Predicates.and(
                            isLayerInfo, workspaceLayerFilter, enabledFilter, storeEnabledFilter, advertisedFilter);
        }
        else {
            layerFilter =
                    Predicates.and(
                            isLayerInfo, enabledFilter, storeEnabledFilter, advertisedFilter);
        }

        // Filter for the LayerGroups
        Filter layerGroupFilter;
        if (workspaceName != null) {
            Filter workspaceLayerGroupFilter = Predicates.equal("workspace.name", workspaceName);
            layerGroupFilter =
                    Predicates.and(
                            isLayerGroupInfo,
                            workspaceLayerGroupFilter,
//                            nonContainerGroup,
                            enabledLayerGroup,
                            advertisedLayerGroup);
        }
        else {
            layerGroupFilter =
                    Predicates.and(
                            isLayerGroupInfo,
//                            nonContainerGroup,
                            enabledLayerGroup,
                            advertisedLayerGroup);
        }
        // Or filter for merging them
        return Predicates.or(layerFilter, layerGroupFilter);
    }

    /**
     * Determine workspace parameter from select input.
     *
     * @param workspaceName
     * @param layerName
     * @return workspace parameter value
     */
    String toWorkspace(String workspaceName, String layerName) {
        if (!Strings.isEmpty(layerName)) {
            if (layerName.contains(":")) {
                return layerName.substring(0,layerName.indexOf(":"));
            } else {
                return null;
            }
        }
        return workspaceName;
    }

    /**
     * Determine layer parameter from select input.
     *
     * @param workspaceName
     * @param layerName
     * @return layer parameter value
     */
    String toLayer(String workspaceName, String layerName) {
        if (!Strings.isEmpty(layerName)) {
            if (layerName.contains(":")) {
                return layerName.substring(layerName.indexOf(":") + 1);
            } else {
                return layerName;
            }
        } else {
            return null;
        }
    }
}
