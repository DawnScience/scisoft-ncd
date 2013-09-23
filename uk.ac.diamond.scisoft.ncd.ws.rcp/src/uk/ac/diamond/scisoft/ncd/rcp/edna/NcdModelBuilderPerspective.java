package uk.ac.diamond.scisoft.ncd.rcp.edna;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.navigator.resources.ProjectExplorer;

import uk.ac.diamond.scisoft.ncd.rcp.edna.views.NcdModelBuilderParametersView;

public class NcdModelBuilderPerspective implements IPerspectiveFactory {

	public static final String ID = "uk.ac.diamond.scisoft.ncd.ws.rcp.ncdmodelbuilderperspective";
	static final String ProjectFolder_ID = "uk.ac.diamond.scisoft.ncd.rcp.projectfolder";
	// Currently defined in uk.ac.diamond.sda.navigator.views class 
	static final String FileView_ID = "uk.ac.diamond.sda.navigator.views.FileView";
	@Override
	public void createInitialLayout(IPageLayout layout) {
		IFolderLayout projectFolderLayout = layout.createFolder(ProjectFolder_ID, IPageLayout.LEFT, 0.3f, IPageLayout.ID_EDITOR_AREA);
		String explorer = ProjectExplorer.VIEW_ID;
		projectFolderLayout.addView(explorer);
		if (layout.getViewLayout(explorer) != null)
			layout.getViewLayout(explorer).setCloseable(false);
		projectFolderLayout.addView(FileView_ID);

		layout.addView(NcdModelBuilderParametersView.ID, IPageLayout.RIGHT, 0.6f, IPageLayout.ID_EDITOR_AREA);

		layout.setEditorAreaVisible(false);
	}

}
