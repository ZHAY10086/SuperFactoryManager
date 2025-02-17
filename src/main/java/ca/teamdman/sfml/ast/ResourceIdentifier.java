package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.program.RegexCache;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.NotImplementedException;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// resourceTypeName resourceNamespace, resourceTypeName name, resource resourceNamespace, resource name
// sfm:item:minecraft:stone
public class ResourceIdentifier<STACK, ITEM, CAP> implements ASTNode, Predicate<Object> {

    public static final ResourceIdentifier<?, ?, ?> MATCH_ALL = new ResourceIdentifier<>(
            ".*",
            ".*"
    );

    public final String resourceTypeNamespace;
    public final String resourceTypeName;
    public final String resourceNamespace;
    public final String resourceName;
    private @Nullable ResourceType<STACK, ITEM, CAP> resourceTypeCache = null;


    private final Predicate<String> resourceNamespacePredicate;
    private final Predicate<String> resourceNamePredicate;

    public ResourceIdentifier(
            String resourceTypeNamespace,
            String resourceTypeName,
            String resourceNamespace,
            String resourceName
    ) {
        // prevent crash on ctrl+space on "Gas::" (capital)
        // we could throw an exception and let it get bubbled to the user
        // but why bother when we know we know lowercasing it fixes it
        resourceTypeNamespace = resourceTypeNamespace.toLowerCase(Locale.ROOT);
        resourceTypeName = resourceTypeName.toLowerCase(Locale.ROOT);

        var check = List.of("fe","rf","energy","power");
        if (resourceTypeNamespace.equals("sfm") && check.contains(resourceTypeName)) {
            resourceTypeName = "forge_energy";
        }
        this.resourceTypeNamespace = resourceTypeNamespace;
        this.resourceTypeName = resourceTypeName;
        this.resourceNamespace = resourceNamespace;
        this.resourceName = resourceName;
        this.resourceNamespacePredicate = RegexCache.buildPredicate(resourceNamespace);
        this.resourceNamePredicate = RegexCache.buildPredicate(resourceName);
    }

    public boolean matchesStack(ResourceLocation stackId) {
        return resourceNamePredicate.test(stackId.getPath()) && resourceNamespacePredicate.test(stackId.getNamespace());
    }

    public ResourceIdentifier(String value) {
        this(SFM.MOD_ID, "item", ".*", value);
    }

    public ResourceIdentifier(String namespace, String value) {
        this(SFM.MOD_ID, "item", namespace, value);
    }

    public ResourceIdentifier(String typeName, String resourceNamespace, String resourceName) {
        this(SFM.MOD_ID, typeName, resourceNamespace, resourceName);
    }

    public static <STACK, ITEM, CAP> ResourceIdentifier<STACK, ITEM, CAP> fromString(String string) {
        var parts = string.split(":");
        if (parts.length == 1) {
            return new ResourceIdentifier<>(parts[0]);
        } else if (parts.length == 2) {
            return new ResourceIdentifier<>(parts[0], parts[1]);
        } else if (parts.length == 3) {
            return new ResourceIdentifier<>(parts[0], parts[1], parts[2]);
        } else if (parts.length == 4) {
            return new ResourceIdentifier<>(parts[0], parts[1], parts[2], parts[3]);
        } else {
            throw new IllegalArgumentException("bad resource id: " + string);
        }
    }

    public void assertValid() throws IllegalArgumentException {
        try {
            if (RegexCache.isRegexPattern(this.resourceNamespace)) {
                Pattern.compile(this.resourceNamespace);
            }
            if (RegexCache.isRegexPattern(this.resourceName)) {
                Pattern.compile(this.resourceName);
            }
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid resource identifier pattern \""
                                               + this
                                               + "\" - "
                                               + e.getMessage());
        }
    }

    public Optional<ResourceLocation> getLocation() {
        try {
            return Optional.of(new ResourceLocation(resourceNamespace, resourceName));
        } catch (ResourceLocationException e) {
            return Optional.empty();
        }
    }

    private static final Map<ResourceIdentifier<?, ?, ?>, List<ResourceIdentifier<?, ?, ?>>> expansionCache = new Object2ObjectOpenHashMap<>();

    public boolean test(Object other) {
        ResourceType<STACK, ITEM, CAP> resourceType = getResourceType();
        return resourceType != null && resourceType.matchesStack(this, other);
    }

    public List<ResourceIdentifier<STACK, ITEM, CAP>> expand() {
        if (this.getResourceType() == SFMResourceTypes.FORGE_ENERGY.get())
            return List.of(new ResourceIdentifier<>(
                    this.resourceTypeNamespace,
                    this.resourceTypeName,
                    "forge",
                    "energy"
            ));
        if (expansionCache.containsKey(this)) {
            //noinspection unchecked,rawtypes
            return (List<ResourceIdentifier<STACK, ITEM, CAP>>) (List) expansionCache.get(this);
        }
        ResourceType<STACK, ITEM, CAP> resourceType = getResourceType();
        try {
            //noinspection DataFlowIssue // resourceType should never be null
            List<ResourceIdentifier<STACK, ITEM, CAP>> rtn = resourceType.getRegistry().getEntries().stream()
                    .filter(e -> matchesStack(e.getKey().location()))
                    .map(e -> new ResourceIdentifier<STACK, ITEM, CAP>(
                            resourceTypeNamespace,
                            resourceTypeName,
                            e.getKey().location().getNamespace(),
                            e.getKey().location().getPath()
                    )).toList();
            //noinspection unchecked,rawtypes
            expansionCache.put(this, (List) rtn);
            return rtn;
        } catch (NotImplementedException e) {
            // some resource types like energy don't actually have a registry
            // the check we do above for forge_energy doesn't easily work for mekanism energy because
            // the mekanism resource types aren't stored in deferred register fields
            // for now, lets just not crash the game at least
            return List.of(this);
        }
    }

    public void setResourceTypeCache(@Nullable ResourceType<STACK, ITEM, CAP> resourceTypeCache) {
        this.resourceTypeCache = resourceTypeCache;
    }

    public @Nullable ResourceType<STACK, ITEM, CAP> getResourceType() {
        if (resourceTypeCache == null) {
            //noinspection unchecked
            setResourceTypeCache((ResourceType<STACK, ITEM, CAP>) SFMResourceTypes.fastLookup(
                    resourceTypeNamespace,
                    resourceTypeName
            ));
        }
        return resourceTypeCache;
    }

    @Override
    public String toString() {
        return resourceTypeNamespace + ":" + resourceTypeName + ":" + resourceNamespace + ":" + resourceName;
    }

    // todo: make this a ShortStatement impl
    public String toStringCondensed() {
        String rtn;
        if (resourceTypeNamespace.equals(SFM.MOD_ID) && resourceTypeName.equals("item")) {
            if (resourceNamespace.equals(".*")) {
                if (resourceName.equals(".*")) {
                    rtn = "item::";
                } else if (RegexCache.isRegexPattern(resourceName)) {
                    rtn = "\""+resourceName+"\"";
                } else {
                    rtn = resourceName;
                }
            } else {
                if (RegexCache.isRegexPattern(resourceName) || RegexCache.isRegexPattern(resourceNamespace)) {
                    rtn = "\"" + resourceNamespace + ":" + resourceName + "\"";
                } else {
                    rtn = resourceNamespace + ":" + resourceName;
                }
            }
        } else if (
                resourceTypeNamespace.equals(SFM.MOD_ID)
                && resourceTypeName.equals("forge_energy")
                && getLocation()
                        .filter(rl -> rl.equals(new ResourceLocation("forge", "energy")))
                        .isPresent()
        ) {
            rtn = "forge_energy::";
        } else if (resourceTypeNamespace.equals(SFM.MOD_ID)) {
            if (RegexCache.isRegexPattern(resourceNamespace) || RegexCache.isRegexPattern(resourceName)) {
                rtn = "\"" + resourceTypeName + ":" + resourceNamespace + ":" + resourceName + "\"";
            } else {
                rtn = resourceTypeName + resourceNamespace + ":" + resourceName;
            }
        } else {
            if (RegexCache.isRegexPattern(resourceNamespace) || RegexCache.isRegexPattern(resourceName)) {
                rtn = "\"" + resourceTypeNamespace + ":" + resourceTypeName + ":" + resourceNamespace + ":" + resourceName + "\"";
            } else {
                rtn = resourceTypeNamespace + ":" + resourceTypeName + ":" + resourceNamespace + ":" + resourceName;
            }
        }
        return rtn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceIdentifier<?, ?, ?> that = (ResourceIdentifier<?, ?, ?>) o;
        return Objects.equals(resourceTypeNamespace, that.resourceTypeNamespace)
               && Objects.equals(resourceTypeName, that.resourceTypeName)
               && Objects.equals(resourceNamespace, that.resourceNamespace)
               && Objects.equals(resourceName, that.resourceName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceTypeNamespace, resourceTypeName, resourceNamespace, resourceName);
    }

    public boolean usesRegex() {
        return RegexCache.isRegexPattern(resourceNamespace) || RegexCache.isRegexPattern(resourceName);
    }
}
