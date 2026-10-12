import type {ReactNode} from 'react';
import {useThemeConfig} from '@docusaurus/theme-common';
import {useNavbarSecondaryMenu} from '@docusaurus/theme-common/internal';
import Translate from '@docusaurus/Translate';
import Icon from '@site/src/components/Icon';

// Same behaviour as the stock component; the back bar gets a hairline arrow instead of the "←" character
// and a text label the stylesheet can size (see .navbar-sidebar__back in custom.css).
export default function NavbarMobileSidebarSecondaryMenu(): ReactNode {
  const isPrimaryMenuEmpty = useThemeConfig().navbar.items.length === 0;
  const secondaryMenu = useNavbarSecondaryMenu();
  return (
    <>
      {/* edge-case: prevent returning to the primaryMenu when it's empty */}
      {!isPrimaryMenuEmpty && (
        <button type="button" className="clean-btn navbar-sidebar__back" onClick={() => secondaryMenu.hide()}>
          <Icon name="arrow-left" size={18} />
          <Translate
            id="coco.navbar.mobileSidebar.back"
            description="Back button of the narrow-window navigation drawer, from the docs sidebar to the main menu">
            回到主菜单
          </Translate>
        </button>
      )}
      {secondaryMenu.content}
    </>
  );
}
