/*
 * Hibernate Validator, declare and validate application constraints
 *
 * License: Apache License, Version 2.0
 * See the license.txt file in the root directory or <http://www.apache.org/licenses/LICENSE-2.0>.
 */
package org.hibernate.validator.internal.xml;

import static org.hibernate.validator.internal.util.CollectionHelper.newArrayList;

import java.lang.annotation.ElementType;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.validator.internal.metadata.cascading.CascadingTypeParameter;
import org.hibernate.validator.internal.metadata.core.AnnotationProcessingOptionsImpl;
import org.hibernate.validator.internal.metadata.core.MetaConstraint;
import org.hibernate.validator.internal.metadata.location.ConstraintLocation;
import org.hibernate.validator.internal.metadata.raw.ConfigurationSource;
import org.hibernate.validator.internal.metadata.raw.ConstrainedParameter;
import org.hibernate.validator.internal.util.ReflectionHelper;
import org.hibernate.validator.internal.xml.ContainerElementTypeConfigurationBuilder.ContainerElementTypeConfiguration;
import org.hibernate.validator.internal.xml.binding.ConstraintType;
import org.hibernate.validator.internal.xml.binding.ParameterType;

/**
 * Builder for constraint parameters.
 *
 * @author Hardy Ferentschik
 * @author Guillaume Smet
 */
class ConstrainedParameterBuilder {

	private final GroupConversionBuilder groupConversionBuilder;
	private final MetaConstraintBuilder metaConstraintBuilder;
	private final AnnotationProcessingOptionsImpl annotationProcessingOptions;

	ConstrainedParameterBuilder(MetaConstraintBuilder metaConstraintBuilder,
			GroupConversionBuilder groupConversionBuilder,
			AnnotationProcessingOptionsImpl annotationProcessingOptions) {
		this.metaConstraintBuilder = metaConstraintBuilder;
		this.groupConversionBuilder = groupConversionBuilder;
		this.annotationProcessingOptions = annotationProcessingOptions;
	}

	List<ConstrainedParameter> buildConstrainedParameters(List<ParameterType> parameterList,
																		Executable executable,
																		String defaultPackage) {
		List<ConstrainedParameter> constrainedParameters = newArrayList();
		int i = 0;
		for ( ParameterType parameterType : parameterList ) {
			ConstraintLocation constraintLocation = ConstraintLocation.forParameter( executable, i );
			Type type = ReflectionHelper.typeOf( executable, i );

			Set<MetaConstraint<?>> metaConstraints = new HashSet<>();
			for ( ConstraintType constraint : parameterType.getConstraint() ) {
				MetaConstraint<?> metaConstraint = metaConstraintBuilder.buildMetaConstraint(
						constraintLocation,
						constraint,
						ElementType.PARAMETER,
						defaultPackage,
						null
				);
				metaConstraints.add( metaConstraint );
			}

			ContainerElementTypeConfigurationBuilder containerElementTypeConfigurationBuilder = new ContainerElementTypeConfigurationBuilder(
					metaConstraintBuilder, constraintLocation, defaultPackage );
			ContainerElementTypeConfiguration containerElementTypeConfiguration = containerElementTypeConfigurationBuilder
					.build( parameterType.getContainerElementType(), type );

			List<CascadingTypeParameter> cascadingTypeParameters = new ArrayList<>( containerElementTypeConfiguration.getCascadingTypeParameters().size() + 1 );
			cascadingTypeParameters.addAll( containerElementTypeConfiguration.getCascadingTypeParameters() );
			addCascadedTypeParameterForParameter( cascadingTypeParameters, type, parameterType.getValid() != null );

			Map<Class<?>, Class<?>> groupConversions = groupConversionBuilder.buildGroupConversionMap(
					parameterType.getConvertGroup(),
					defaultPackage
			);

			// ignore annotations
			if ( parameterType.getIgnoreAnnotations() != null ) {
				annotationProcessingOptions.ignoreConstraintAnnotationsOnParameter(
						executable,
						i,
						parameterType.getIgnoreAnnotations()
				);
			}

			ConstrainedParameter constrainedParameter = new ConstrainedParameter(
					ConfigurationSource.XML,
					executable,
					type,
					i,
					metaConstraints,
					containerElementTypeConfiguration.getMetaConstraints(),
					groupConversions,
					cascadingTypeParameters
			);
			constrainedParameters.add( constrainedParameter );
			i++;
		}

		return constrainedParameters;
	}

	private void addCascadedTypeParameterForParameter(List<CascadingTypeParameter> cascadingTypeParameters, Type parameterType, boolean isCascaded) {
		if ( isCascaded ) {
			cascadingTypeParameters.add( ReflectionHelper.getClassFromType( parameterType ).isArray()
					? CascadingTypeParameter.arrayElement( parameterType )
					: CascadingTypeParameter.annotatedObject( parameterType ) );
		}
	}
}
