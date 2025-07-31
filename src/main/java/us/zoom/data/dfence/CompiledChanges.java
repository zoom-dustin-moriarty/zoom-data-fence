package us.zoom.data.dfence;

import com.google.common.collect.ImmutableList;

import java.util.List;

public record CompiledChanges(
        String roleId, String roleName, List<String> roleCreationStatements, List<List<String>> roleGrantStatements) {
    public CompiledChanges {
        roleCreationStatements = ImmutableList.copyOf(roleCreationStatements);
        roleGrantStatements = ImmutableList.copyOf(roleGrantStatements);
    }

    public Boolean containsChanges() {
        return !roleCreationStatements.isEmpty() || !roleGrantStatements.isEmpty();
    }
}
