/*******************************************************************************
 * Copyright (c) 2014
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Benjamin Klatt - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.splevo.jamopp.diffing;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.diff.DefaultDiffEngine;
import org.eclipse.emf.compare.diff.FeatureFilter;
import org.eclipse.emf.compare.diff.IDiffEngine;
import org.eclipse.emf.compare.diff.IDiffProcessor;
import org.eclipse.emf.compare.match.DefaultMatchEngine;
import org.eclipse.emf.compare.match.IMatchEngine;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryRegistryImpl;
import org.eclipse.emf.compare.postprocessor.BasicPostProcessorDescriptorImpl;
import org.eclipse.emf.compare.postprocessor.IPostProcessor;
import org.eclipse.emf.compare.postprocessor.PostProcessorDescriptorRegistryImpl;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.compare.utils.EqualityHelper;
import org.eclipse.emf.compare.utils.IEqualityHelper;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.emftext.commons.layout.LayoutPackage;
import org.emftext.language.java.JavaPackage;
import org.splevo.diffing.DiffingException;
import org.splevo.diffing.DiffingNotSupportedException;
import org.splevo.diffing.JavaDiffer;
import org.splevo.diffing.match.HierarchicalMatchEngine.EqualityStrategy;
import org.splevo.diffing.match.HierarchicalMatchEngine.IgnoreStrategy;
import org.splevo.diffing.match.HierarchicalMatchEngineFactory;
import org.splevo.diffing.match.HierarchicalStrategyResourceMatcher;
import org.splevo.extraction.SoftwareModelExtractionException;
import org.splevo.jamopp.diffing.diff.JaMoPPDiffBuilder;
import org.splevo.jamopp.diffing.diff.JaMoPPFeatureFilter;
import org.splevo.jamopp.diffing.match.JaMoPPEqualityHelper;
import org.splevo.jamopp.diffing.match.JaMoPPEqualityStrategy;
import org.splevo.jamopp.diffing.match.JaMoPPIgnoreStrategy;
import org.splevo.jamopp.diffing.postprocessor.JaMoPPPostProcessor;
import org.splevo.jamopp.diffing.scope.JavaModelMatchScope;
import org.splevo.jamopp.diffing.scope.PackageIgnoreChecker;
import org.splevo.jamopp.diffing.similarity.SimilarityChecker;
import org.splevo.jamopp.extraction.JaMoPPSoftwareModelExtractor;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;

/**
 * Differ for JaMoPP software models.
 *
 * <p>
 * <strong>Ignored files</strong><br>
 * By default, the differ ignores all package-info.java.xmi model files.<br>
 * The option {@link JaMoPPDiffer#OPTION_JAMOPP_IGNORE_FILES} can be provided as diffing option to
 * the doDiff() method to change this default behavior.
 * </p>
 *
 * @author Benjamin Klatt
 *
 */
public class JaMoPPDiffer extends JavaDiffer {

    /** Option key for java packages to ignore. */
    public static final String OPTION_JAMOPP_IGNORE_FILES = "jamopp.ignoreFiles";

    private static final String LABEL = "JaMoPP Java Differ";
    private static final String ID = "org.splevo.jamopp.differ";
    private static Logger logger = Logger.getLogger(JaMoPPDiffer.class);

    /**
     * Load the source models from the according directories and perform the difference analysis of
     * the loaded {@link ResourceSet}s. <br>
     * {@inheritDoc}
     *
     * @return null if no supported source models available.
     * @throws DiffingNotSupportedException
     *             Thrown if a diffing cannot be done for the provided models.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Comparison doDiff(java.net.URI leadingModelDirectory, java.net.URI integrationModelDirectory,
            Map<String, Object> diffingOptions) throws DiffingException, DiffingNotSupportedException {

        final List<String> ignoreFiles;
        if (diffingOptions.containsKey(OPTION_JAMOPP_IGNORE_FILES)) {
            ignoreFiles = (List<String>) diffingOptions.get(OPTION_JAMOPP_IGNORE_FILES);
        } else {
            ignoreFiles = Lists.asList("package-info.java", new String[] {});
        }

        logger.info("Load source models");
        ResourceSet resourceSetLeading = loadResourceSetRecursively(leadingModelDirectory, ignoreFiles);
        ResourceSet resourceSetIntegration = loadResourceSetRecursively(integrationModelDirectory, ignoreFiles);

        return doDiff(resourceSetLeading, resourceSetIntegration, diffingOptions);
    }

    /**
     * Diffing the models contained in the provided resource sets.<br>
     *
     * {@inheritDoc}
     *
     * @return null if no supported source models available.
     * @throws DiffingNotSupportedException
     *             Thrown if no reasonable JaMoPP model is contained in the resource sets.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Comparison doDiff(ResourceSet resourceSetLeading, ResourceSet resourceSetIntegration,
            Map<String, Object> diffingOptions) throws DiffingException, DiffingNotSupportedException {

        final List<String> ignorePackages = (List<String>) diffingOptions.get(OPTION_JAVA_IGNORE_PACKAGES);
        PackageIgnoreChecker packageIgnoreChecker = new PackageIgnoreChecker(ignorePackages);

        EMFCompare comparator = initCompare(packageIgnoreChecker);

        // Compare the two models
        // In comparison, the left side is always the changed one.
        // push in the integration model first
        IComparisonScope scope = new JavaModelMatchScope(resourceSetIntegration, resourceSetLeading,
                packageIgnoreChecker);

        Comparison comparisonModel = comparator.compare(scope);

        return comparisonModel;

    }

    /**
     * Initialize the compare engine.
     *
     * @param packageIgnoreChecker
     *            The checker to decide if an element is within a package to ignore.
     * @return The prepared emf compare engine.
     */
    private EMFCompare initCompare(PackageIgnoreChecker packageIgnoreChecker) {
        SimilarityChecker similarityChecker = new SimilarityChecker();
        final LoadingCache<EObject, org.eclipse.emf.common.util.URI> cache = initEqualityCache();
        IEqualityHelper equalityHelper = new JaMoPPEqualityHelper(cache, similarityChecker);
        IMatchEngine.Factory.Registry matchEngineRegistry = initMatchEngine(equalityHelper, packageIgnoreChecker,
                similarityChecker);
        IPostProcessor.Descriptor.Registry<?> postProcessorRegistry = initPostProcessors(packageIgnoreChecker);
        IDiffEngine diffEngine = initDiffEngine(packageIgnoreChecker);
        EMFCompare comparator = initComparator(matchEngineRegistry, postProcessorRegistry, diffEngine);
        return comparator;
    }

    /**
     * Initialize a cache to be used by the equality helper.
     *
     * @return The ready to use cache.
     */
    private LoadingCache<EObject, org.eclipse.emf.common.util.URI> initEqualityCache() {
        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder().maximumSize(
                DefaultMatchEngine.DEFAULT_EOBJECT_URI_CACHE_MAX_SIZE);
        final LoadingCache<EObject, org.eclipse.emf.common.util.URI> cache = EqualityHelper
                .createDefaultCache(cacheBuilder);
        return cache;
    }

    /**
     * Initialize the post processors and build an according registry.
     *
     * @param packageIgnoreChecker
     *            The checker if an element belongs to an ignored package.
     * @return The prepared registry with references to the post processors.
     */
    private IPostProcessor.Descriptor.Registry<String> initPostProcessors(PackageIgnoreChecker packageIgnoreChecker) {
        IPostProcessor customPostProcessor = new JaMoPPPostProcessor();
        Pattern any = Pattern.compile(".*");
        IPostProcessor.Descriptor descriptor = new BasicPostProcessorDescriptorImpl(customPostProcessor, any, any);
        IPostProcessor.Descriptor.Registry<String> postProcessorRegistry = new PostProcessorDescriptorRegistryImpl<String>();
        postProcessorRegistry.put(JaMoPPPostProcessor.class.getName(), descriptor);
        return postProcessorRegistry;
    }

    /**
     * Init the comparator instance to be used for comparison.
     *
     * @param matchEngineRegistry
     *            The registry containing the match engines to be used.
     * @param postProcessorRegistry
     *            Registry for post processors to be executed.
     * @param diffEngine
     *            The diff engine to run.
     * @return The prepared comparator instance.
     */
    private EMFCompare initComparator(IMatchEngine.Factory.Registry matchEngineRegistry,
            IPostProcessor.Descriptor.Registry<?> postProcessorRegistry, IDiffEngine diffEngine) {
        EMFCompare.Builder builder = EMFCompare.builder();
        builder.setDiffEngine(diffEngine);
        builder.setMatchEngineFactoryRegistry(matchEngineRegistry);
        builder.setPostProcessorRegistry(postProcessorRegistry);
        EMFCompare comparator = builder.build();
        return comparator;
    }

    /**
     * Initialize the diff engine with the diff processor and feature filters to be used.
     *
     * @param packageIgnoreChecker
     *            Checker to decide if an element is in a package to ignore.
     * @return The ready-to-use diff engine.
     */
    private IDiffEngine initDiffEngine(final PackageIgnoreChecker packageIgnoreChecker) {
        IDiffProcessor diffProcessor = new JaMoPPDiffBuilder(packageIgnoreChecker);
        IDiffEngine diffEngine = new DefaultDiffEngine(diffProcessor) {
            @Override
            protected FeatureFilter createFeatureFilter() {
                return new JaMoPPFeatureFilter(packageIgnoreChecker);
            }
        };
        return diffEngine;
    }

    /**
     * Initialize and configure the match engines to be used.
     *
     * @param equalityHelper
     *            The equality helper to be used during the diff process.
     * @param packageIgnoreChecker
     *            The package ignore checker to use in the match engine.
     * @param similarityChecker
     *            The similarity checker to use in the match engine.
     *
     * @return The registry containing all prepared match engines
     */
    private IMatchEngine.Factory.Registry initMatchEngine(IEqualityHelper equalityHelper,
            PackageIgnoreChecker packageIgnoreChecker, SimilarityChecker similarityChecker) {

        EqualityStrategy equalityStrategy = new JaMoPPEqualityStrategy(similarityChecker);
        IgnoreStrategy ignoreStrategy = new JaMoPPIgnoreStrategy(packageIgnoreChecker);

        IMatchEngine.Factory matchEngineFactory = new HierarchicalMatchEngineFactory(equalityHelper, equalityStrategy,
                ignoreStrategy, new HierarchicalStrategyResourceMatcher());
        matchEngineFactory.setRanking(20);

        IMatchEngine.Factory.Registry matchEngineRegistry = new MatchEngineFactoryRegistryImpl();
        matchEngineRegistry.add(matchEngineFactory);

        return matchEngineRegistry;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public void init() {
        JavaPackage.eINSTANCE.eClass();
        LayoutPackage.eINSTANCE.eClass();
    }

    /**
     * Recursively load all files within a directory into a resource set.
     *
     * @param baseDirectory
     *            The root directory to search files in.
     * @param ignoreFiles
     *            A list of filenames to ignore during load.
     * @return The prepared resource set.
     */
    private ResourceSet loadResourceSetRecursively(java.net.URI baseDirectory, List<String> ignoreFiles) {

        JaMoPPSoftwareModelExtractor extractor = new JaMoPPSoftwareModelExtractor();
        List<URI> projectPaths = new ArrayList<URI>();
        projectPaths.add(URI.createFileURI(new File(baseDirectory).getAbsolutePath()));
        try {
            return extractor.extractSoftwareModel(projectPaths, new NullProgressMonitor());
        } catch (SoftwareModelExtractionException e) {
            logger.error("Failed to load resource set", e);
        }

        return new ResourceSetImpl();
    }
}
